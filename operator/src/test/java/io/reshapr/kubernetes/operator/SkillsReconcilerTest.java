/*
 * Copyright The Reshapr Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reshapr.kubernetes.operator;

import io.reshapr.client.model.ArtifactType;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.skills.v1alpha1.SkillItem;
import io.reshapr.kubernetes.api.skills.v1alpha1.Skills;
import io.reshapr.kubernetes.api.skills.v1alpha1.SkillsSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SkillsReconciler}.
 * @author vaishnav
 */
class SkillsReconcilerTest {

   private final SkillsReconciler reconciler = new SkillsReconciler();
   private final ObjectMapper objectMapper = new ObjectMapper();

   private Skills buildSkills() {
      ServiceRef serviceRef = new ServiceRef();
      serviceRef.setName("open-meteo-api");
      serviceRef.setVersion("1.0");

      SkillItem item = new SkillItem();
      item.setRemoteContent("https://skills.example.com/open-meteo-forecast");
      item.setPath("weather/forecast");
      item.setFiles(List.of("references/VARIABLES.md"));
      item.setMeta(Map.of("owner", "weather-team"));

      SkillsSpec spec = new SkillsSpec();
      spec.setService(serviceRef);
      spec.setSkills(Map.of("open-meteo-forecast", item));

      ObjectMeta meta = new ObjectMeta();
      meta.setName("my-skills");
      Skills skills = new Skills();
      skills.setMetadata(meta);
      skills.setSpec(spec);
      return skills;
   }

   @Test
   void testArtifactTypeAndName() {
      Skills skills = buildSkills();
      assertEquals(ArtifactType.RESHAPR_SKILLS, reconciler.getArtifactType());
      assertEquals("my-skills", reconciler.getArtifactName(skills));
      assertEquals("open-meteo-api", reconciler.getServiceRef(skills).getName());
   }

   @Test
   void testArtifactContent() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(buildSkills()));
      assertEquals("reshapr.io/v1alpha1", root.get("apiVersion").asText());
      assertEquals("Skills", root.get("kind").asText());
      assertEquals("open-meteo-api", root.get("service").get("name").asText());
      assertEquals("1.0", root.get("service").get("version").asText());

      JsonNode skill = root.get("skills").get("open-meteo-forecast");
      assertEquals("https://skills.example.com/open-meteo-forecast", skill.get("remoteContent").asText());
      assertEquals("weather/forecast", skill.get("path").asText());
      assertEquals("references/VARIABLES.md", skill.get("files").get(0).asText());
      assertEquals("weather-team", skill.get("_meta").get("owner").asText());
      assertFalse(skill.has("meta"), "Metadata must be serialized as '_meta' to match the control plane schema");
   }

   @Test
   void testArtifactContentOmitsOptionalFields() throws Exception {
      Skills skills = buildSkills();
      SkillItem minimal = new SkillItem();
      minimal.setRemoteContent("http://localhost:3040/pastry-ordering");
      skills.getSpec().setSkills(Map.of("pastry-ordering", minimal));

      JsonNode skill = objectMapper.readTree(reconciler.getArtifactContent(skills))
            .get("skills").get("pastry-ordering");
      assertEquals("http://localhost:3040/pastry-ordering", skill.get("remoteContent").asText());
      assertFalse(skill.has("path"));
      assertFalse(skill.has("files"));
      assertFalse(skill.has("_meta"));
   }

   @Test
   void testArtifactContentWithoutSpec() throws Exception {
      Skills skills = buildSkills();
      skills.setSpec(null);

      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(skills));
      assertEquals("Skills", root.get("kind").asText());
      assertFalse(root.has("service"));
      assertFalse(root.has("skills"));
      assertNull(reconciler.getServiceRef(skills));
      assertFalse(reconciler.isKeepOnDelete(skills));
   }

   @Test
   void testUpdateStatus() {
      Skills skills = buildSkills();
      assertNull(reconciler.getArtifactId(skills));

      reconciler.updateStatus(skills, "svc", "art", Status.READY, "ok");
      assertEquals(Status.READY, skills.getStatus().getState());
      assertEquals("svc", skills.getStatus().getServiceId());
      assertEquals("art", skills.getStatus().getArtifactId());
      assertEquals("ok", skills.getStatus().getMessage());
      assertEquals("art", reconciler.getArtifactId(skills));

      // Null values must not override previously set ones.
      reconciler.updateStatus(skills, null, null, Status.ERROR, "boom");
      assertEquals(Status.ERROR, skills.getStatus().getState());
      assertEquals("svc", skills.getStatus().getServiceId());
      assertEquals("art", skills.getStatus().getArtifactId());
      assertEquals("boom", skills.getStatus().getMessage());
   }

   @Test
   void testReconcilerImplementsCleanerForArtifactDeletion() {
      assertTrue(Cleaner.class.isAssignableFrom(SkillsReconciler.class),
            "SkillsReconciler must implement Cleaner to delete artifacts from control plane on CR deletion");
   }

   @Test
   void testCleanup_deletesArtifactWhenPresent() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ApiClient apiClient = mock(ApiClient.class);
      when(factory.createAuthenticatedApiClient(any(), any())).thenReturn(apiClient);

      SkillsReconciler customReconciler = new SkillsReconciler(factory, null, objectMapper);
      Skills skills = buildSkills();
      skills.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(skills, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl control = customReconciler.cleanup(skills, null);

         assertTrue(control.isRemoveFinalizer());
         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).deleteArtifact("art-123");
      }
   }

   @Test
   void testCleanup_handles404Cleanly() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ApiClient apiClient = mock(ApiClient.class);
      when(factory.createAuthenticatedApiClient(any(), any())).thenReturn(apiClient);

      SkillsReconciler customReconciler = new SkillsReconciler(factory, null, objectMapper);
      Skills skills = buildSkills();
      skills.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(skills, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> doThrow(new ApiException(404, "Not Found")).when(mock).deleteArtifact("art-123"))) {
         DeleteControl control = customReconciler.cleanup(skills, null);

         assertTrue(control.isRemoveFinalizer());
         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).deleteArtifact("art-123");
      }
   }

   @Test
   void testCleanup_honorsKeepOnDelete() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      SkillsReconciler customReconciler = new SkillsReconciler(factory, null, objectMapper);
      Skills skills = buildSkills();
      skills.getSpec().setKeepOnDelete(true);
      customReconciler.updateStatus(skills, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl control = customReconciler.cleanup(skills, null);

         assertTrue(control.isRemoveFinalizer());
         assertEquals(0, mocked.constructed().size(), "DefaultApi should not be initialized when keepOnDelete is true");
      }
   }
}

