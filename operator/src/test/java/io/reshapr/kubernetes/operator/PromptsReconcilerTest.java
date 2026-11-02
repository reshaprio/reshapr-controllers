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
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptArgument;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptItem;
import io.reshapr.kubernetes.api.prompts.v1alpha1.Prompts;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptsSpec;

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
 * Unit tests for {@link PromptsReconciler}.
 * @author laurent
 */
class PromptsReconcilerTest {

   private final PromptsReconciler reconciler = new PromptsReconciler();
   private final ObjectMapper objectMapper = new ObjectMapper();

   private Prompts buildPrompts() {
      ServiceRef serviceRef = new ServiceRef();
      serviceRef.setName("open-meteo-api");
      serviceRef.setVersion("1.0");

      PromptArgument arg = new PromptArgument();
      arg.setName("city");
      arg.setRequired(true);
      PromptItem item = new PromptItem();
      item.setDescription("Weather summary");
      item.setArguments(List.of(arg));
      item.setResult("Forecast for ${city}");

      PromptsSpec spec = new PromptsSpec();
      spec.setService(serviceRef);
      spec.setPrompts(Map.of("weather-summary", item));

      ObjectMeta meta = new ObjectMeta();
      meta.setName("my-prompts");
      Prompts prompts = new Prompts();
      prompts.setMetadata(meta);
      prompts.setSpec(spec);
      return prompts;
   }

   @Test
   void testArtifactTypeAndName() {
      Prompts prompts = buildPrompts();
      assertEquals(ArtifactType.RESHAPR_PROMPTS, reconciler.getArtifactType());
      assertEquals("my-prompts", reconciler.getArtifactName(prompts));
      assertEquals("open-meteo-api", reconciler.getServiceRef(prompts).getName());
   }

   @Test
   void testArtifactContent() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(buildPrompts()));
      assertEquals("Prompts", root.get("kind").asText());
      assertEquals("1.0", root.get("service").get("version").asText());
      JsonNode prompt = root.get("prompts").get("weather-summary");
      assertEquals("city", prompt.get("arguments").get(0).get("name").asText());
      assertEquals("Forecast for ${city}", prompt.get("result").asText());
   }

   @Test
   void testUpdateStatus() {
      Prompts prompts = buildPrompts();
      reconciler.updateStatus(prompts, "svc", "art", Status.READY, "ok");
      assertEquals(Status.READY, prompts.getStatus().getState());
      assertEquals("svc", prompts.getStatus().getServiceId());
      assertEquals("art", prompts.getStatus().getArtifactId());
      assertEquals("ok", prompts.getStatus().getMessage());
   }

   @Test
   void testReconcilerImplementsCleanerForArtifactDeletion() {
      assertTrue(Cleaner.class.isAssignableFrom(PromptsReconciler.class),
            "PromptsReconciler must implement Cleaner to delete artifacts from control plane on CR deletion");
   }

   @Test
   void testCleanup_deletesArtifactWhenPresent() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ApiClient apiClient = mock(ApiClient.class);
      when(factory.createAuthenticatedApiClient(any(), any())).thenReturn(apiClient);

      PromptsReconciler customReconciler = new PromptsReconciler(factory, null, objectMapper);
      Prompts prompts = buildPrompts();
      prompts.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(prompts, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl control = customReconciler.cleanup(prompts, null);

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

      PromptsReconciler customReconciler = new PromptsReconciler(factory, null, objectMapper);
      Prompts prompts = buildPrompts();
      prompts.getMetadata().setAnnotations(Map.of(
            ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system",
            ReshaprAnnotations.ORGANIZATION, "reshapr"
      ));
      customReconciler.updateStatus(prompts, "svc-1", "art-123", Status.READY, "ok");

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> doThrow(new ApiException(404, "Not Found")).when(mock).deleteArtifact("art-123"))) {
         DeleteControl control = customReconciler.cleanup(prompts, null);

         assertTrue(control.isRemoveFinalizer());
         DefaultApi mockedApi = mocked.constructed().get(0);   
         verify(mockedApi).deleteArtifact("art-123");
      }
   }
}

