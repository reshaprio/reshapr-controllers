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
import io.reshapr.kubernetes.api.tools.v1alpha1.CustomTools;
import io.reshapr.kubernetes.api.tools.v1alpha1.CustomToolsStatus;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.client.ArtifactAttachClient;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class CustomToolsReconciler extends BaseArtifactReconciler<CustomTools> {

   private final ObjectMapper objectMapper;

   CustomToolsReconciler() {
      this.objectMapper = new ObjectMapper();
   }

   @Inject
   public CustomToolsReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactAttachClient artifactAttachClient, ObjectMapper objectMapper) {
      super(apiClientFactory, artifactAttachClient);
      this.objectMapper = objectMapper;
   }

   @Override
   protected ServiceRef getServiceRef(CustomTools resource) {
      return resource.getSpec() != null ? resource.getSpec().getService() : null;
   }

   @Override
   protected ArtifactType getArtifactType() {
      return ArtifactType.RESHAPR_CUSTOM_TOOLS;
   }

   @Override
   protected String getArtifactContent(CustomTools resource) throws Exception {
      if (resource.getSpec() == null || resource.getSpec().getCustomTools() == null) {
         return "{}";
      }
      return objectMapper.writeValueAsString(resource.getSpec().getCustomTools());
   }

   @Override
   protected String getArtifactName(CustomTools resource) {
      return resource.getMetadata().getName();
   }

   @Override
   protected void updateStatus(CustomTools resource, String serviceId, String artifactId, Status status, String message) {
      CustomToolsStatus s = resource.getStatus();
      if (s == null) {
         s = new CustomToolsStatus();
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
