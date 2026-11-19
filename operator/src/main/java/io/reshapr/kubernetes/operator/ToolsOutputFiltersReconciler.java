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
import io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1.ToolsOutputFilters;
import io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1.ToolsOutputFiltersStatus;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.client.ArtifactAttachClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

/**
 * Reconciler for ToolsOutputFilters custom resource.
 * Keeps a reShapr {@link ToolsOutputFilters} custom resource in sync with its counterpart in
 * the control plane as a {@code RESHAPR_TOOLS_OUTPUT_FILTERS} artifact.
 *
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class ToolsOutputFiltersReconciler extends BaseArtifactReconciler<ToolsOutputFilters> {

   private final ObjectMapper objectMapper;

   ToolsOutputFiltersReconciler() {
      this.objectMapper = new ObjectMapper();
   }

   @Inject
   public ToolsOutputFiltersReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactAttachClient artifactAttachClient, ObjectMapper objectMapper) {
      super(apiClientFactory, artifactAttachClient);
      this.objectMapper = objectMapper;
   }

   @Override
   protected ServiceRef getServiceRef(ToolsOutputFilters resource) {
      return resource.getSpec() != null ? resource.getSpec().getService() : null;
   }

   @Override
   protected ArtifactType getArtifactType() {
      return ArtifactType.RESHAPR_TOOLS_OUTPUT_FILTERS;
   }

   @Override
   protected String getArtifactContent(ToolsOutputFilters resource) throws Exception {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("apiVersion", "reshapr.io/v1alpha1");
      root.put("kind", "ToolsOutputFilters");
      if (resource.getSpec() != null) {
         if (resource.getSpec().getService() != null) {
            root.set("service", objectMapper.valueToTree(resource.getSpec().getService()));
         }
         if (resource.getSpec().getFilters() != null) {
            root.set("filters", objectMapper.valueToTree(resource.getSpec().getFilters()));
         }
      }
      return objectMapper.writeValueAsString(root);
   }

   @Override
   protected String getArtifactName(ToolsOutputFilters resource) {
      return resource.getMetadata().getName();
   }

   @Override
   protected String getArtifactId(ToolsOutputFilters resource) {
      return resource.getStatus() != null ? resource.getStatus().getArtifactId() : null;
   }

   @Override
   protected boolean isKeepOnDelete(ToolsOutputFilters resource) {
      return resource.getSpec() != null && resource.getSpec().isKeepOnDelete();
   }

   @Override
   protected void updateStatus(ToolsOutputFilters resource, String serviceId, String artifactId, Status status, String message) {
      ToolsOutputFiltersStatus s = resource.getStatus();
      if (s == null) {
         s = new ToolsOutputFiltersStatus();
         resource.setStatus(s);
      }
      if (serviceId != null) {
         s.setServiceId(serviceId);
      }
      if (artifactId != null) {
         s.setArtifactId(artifactId);
      }
      if (status != null) {
         s.setState(status);
      }
      if (message != null) {
         s.setMessage(message);
      }
   }
}
