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
import io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1.OutputFilterItem;
import io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1.ToolsOutputFilters;
import io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1.ToolsOutputFiltersSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToolsOutputFiltersReconciler}.
 * @author laurent
 */
class ToolsOutputFiltersReconcilerTest {

   private final ToolsOutputFiltersReconciler reconciler = new ToolsOutputFiltersReconciler();
   private final ObjectMapper objectMapper = new ObjectMapper();

   private ToolsOutputFilters build() {
      ServiceRef serviceRef = new ServiceRef();
      serviceRef.setName("open-meteo-api");
      serviceRef.setVersion("1.0");

      OutputFilterItem item = new OutputFilterItem();
      item.setJsonRetain(List.of("/daily"));
      item.setCompact(true);

      ToolsOutputFiltersSpec spec = new ToolsOutputFiltersSpec();
      spec.setService(serviceRef);
      spec.setFilters(Map.of("getForecast", item));

      ObjectMeta meta = new ObjectMeta();
      meta.setName("my-filters");
      ToolsOutputFilters filters = new ToolsOutputFilters();
      filters.setMetadata(meta);
      filters.setSpec(spec);
      return filters;
   }

   @Test
   void testArtifactTypeAndName() {
      ToolsOutputFilters filters = build();
      assertEquals(ArtifactType.RESHAPR_TOOLS_OUTPUT_FILTERS, reconciler.getArtifactType());
      assertEquals("my-filters", reconciler.getArtifactName(filters));
      assertEquals("open-meteo-api", reconciler.getServiceRef(filters).getName());
   }

   @Test
   void testArtifactContent() throws Exception {
      JsonNode root = objectMapper.readTree(reconciler.getArtifactContent(build()));
      assertEquals("ToolsOutputFilters", root.get("kind").asText());
      assertEquals("1.0", root.get("service").get("version").asText());
      JsonNode filter = root.get("filters").get("getForecast");
      assertEquals("/daily", filter.get("jsonRetain").get(0).asText());
      assertTrue(filter.get("compact").asBoolean());
      assertFalse(filter.has("convertToToon"));
   }

   @Test
   void testUpdateStatus() {
      ToolsOutputFilters filters = build();
      reconciler.updateStatus(filters, "svc", "art", Status.READY, "ok");
      assertEquals(Status.READY, filters.getStatus().getState());
      assertEquals("art", filters.getStatus().getArtifactId());
   }

   @Test
   void testReconcilerImplementsCleaner() {
      assertTrue(Cleaner.class.isAssignableFrom(ToolsOutputFiltersReconciler.class));
   }
}
