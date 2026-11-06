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
import io.reshapr.kubernetes.api.skills.v1alpha1.Skills;
import io.reshapr.kubernetes.api.skills.v1alpha1.SkillsStatus;
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
 * Reconciler for Skills custom resource.
 * Keeps a reShapr {@link Skills} custom resource in sync with its counterpart in
 * the control plane as a {@code RESHAPR_SKILLS} artifact. The artifact content follows the
 * {@code Skills-v1alpha1} schema of the control plane (reShapr 1.2.0 or later).
 *
 * @author vaishnav
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class SkillsReconciler extends BaseArtifactReconciler<Skills> {

   private final ObjectMapper objectMapper;

   SkillsReconciler() {
      this.objectMapper = new ObjectMapper();
   }

   @Inject
   public SkillsReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactAttachClient artifactAttachClient, ObjectMapper objectMapper) {
      super(apiClientFactory, artifactAttachClient);
      this.objectMapper = objectMapper;
   }

   @Override
   protected ServiceRef getServiceRef(Skills resource) {
      return resource.getSpec() != null ? resource.getSpec().getService() : null;
   }

   @Override
   protected ArtifactType getArtifactType() {
      return ArtifactType.RESHAPR_SKILLS;
   }

   @Override
   protected String getArtifactContent(Skills resource) throws Exception {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("apiVersion", "reshapr.io/v1alpha1");
      root.put("kind", "Skills");
      if (resource.getSpec() != null) {
         if (resource.getSpec().getService() != null) {
            root.set("service", objectMapper.valueToTree(resource.getSpec().getService()));
         }
         if (resource.getSpec().getSkills() != null) {
            root.set("skills", objectMapper.valueToTree(resource.getSpec().getSkills()));
         }
      }
      return objectMapper.writeValueAsString(root);
   }

   @Override
   protected String getArtifactName(Skills resource) {
      return resource.getMetadata().getName();
   }

   @Override
   protected String getArtifactId(Skills resource) {
      return resource.getStatus() != null ? resource.getStatus().getArtifactId() : null;
   }

   @Override
   protected boolean isKeepOnDelete(Skills resource) {
      return resource.getSpec() != null && resource.getSpec().isKeepOnDelete();
   }

   @Override
   protected void updateStatus(Skills resource, String serviceId, String artifactId, Status status, String message) {
      SkillsStatus s = resource.getStatus();
      if (s == null) {
         s = new SkillsStatus();
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

