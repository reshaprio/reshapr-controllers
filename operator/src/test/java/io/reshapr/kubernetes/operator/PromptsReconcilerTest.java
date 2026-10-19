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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
