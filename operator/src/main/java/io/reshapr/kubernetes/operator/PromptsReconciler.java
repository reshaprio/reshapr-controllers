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
import io.reshapr.kubernetes.api.prompts.v1alpha1.Prompts;
import io.reshapr.kubernetes.api.prompts.v1alpha1.PromptsStatus;
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
 * Reconciler for Prompts custom resource.
 * Keeps a reShapr {@link Prompts} custom resource in sync with its counterpart in
 * the control plane as a {@code RESHAPR_PROMPTS} artifact.
 *
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class PromptsReconciler extends BaseArtifactReconciler<Prompts> {

   private final ObjectMapper objectMapper;

   PromptsReconciler() {
      this.objectMapper = new ObjectMapper();
   }

   @Inject
   public PromptsReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactAttachClient artifactAttachClient, ObjectMapper objectMapper) {
      super(apiClientFactory, artifactAttachClient);
      this.objectMapper = objectMapper;
   }

   @Override
   protected ServiceRef getServiceRef(Prompts resource) {
      return resource.getSpec() != null ? resource.getSpec().getService() : null;
   }

   @Override
   protected ArtifactType getArtifactType() {
      return ArtifactType.RESHAPR_PROMPTS;
   }

   @Override
   protected String getArtifactContent(Prompts resource) throws Exception {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("apiVersion", "reshapr.io/v1alpha1");
      root.put("kind", "Prompts");
      if (resource.getSpec() != null) {
         if (resource.getSpec().getService() != null) {
            root.set("service", objectMapper.valueToTree(resource.getSpec().getService()));
         }
         if (resource.getSpec().getPrompts() != null) {
            root.set("prompts", objectMapper.valueToTree(resource.getSpec().getPrompts()));
         }
      }
      return objectMapper.writeValueAsString(root);
   }

   @Override
   protected String getArtifactName(Prompts resource) {
      return resource.getMetadata().getName();
   }

   @Override
   protected void updateStatus(Prompts resource, String serviceId, String artifactId, Status status, String message) {
      PromptsStatus s = resource.getStatus();
      if (s == null) {
         s = new PromptsStatus();
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
