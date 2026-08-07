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

import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.Artifact;
import io.reshapr.client.model.ArtifactType;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.client.ArtifactAttachClient;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.jboss.logging.Logger;

/**
 * Abstract reconciler for GitOps synchronization of Artifacts (like CustomTools, Prompts).
 */
public abstract class BaseArtifactReconciler<R extends CustomResource<?, ?>> extends BaseReshaprReconciler<R> {

   private static final Logger logger = Logger.getLogger(BaseArtifactReconciler.class);

   private ArtifactAttachClient artifactAttachClient;

   protected BaseArtifactReconciler() {
   }

   protected BaseArtifactReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactAttachClient artifactAttachClient) {
      super(apiClientFactory);
      this.artifactAttachClient = artifactAttachClient;
   }

   protected abstract ServiceRef getServiceRef(R resource);
   protected abstract ArtifactType getArtifactType();
   protected abstract String getArtifactContent(R resource) throws Exception;
   protected abstract String getArtifactName(R resource);
   protected abstract void updateStatus(R resource, String serviceId, String artifactId, Status status, String message);

   @Override
   protected UpdateControl<R> doReconcile(R resource, Context<R> context, ApiClient apiClient) {
      String name = resource.getMetadata().getName();
      ServiceRef serviceRef = getServiceRef(resource);

      if (serviceRef == null || serviceRef.getName() == null || serviceRef.getName().isBlank()) {
         String msg = "Missing Service reference in spec";
         logger.warnf("Resource '%s': %s", name, msg);
         updateStatus(resource, null, null, Status.ERROR, msg);
         return UpdateControl.patchStatus(resource);
      }

      try {
         DefaultApi api = new DefaultApi(apiClient);
         io.reshapr.client.model.Service remoteService = findRemoteService(api, serviceRef.getName(), serviceRef.getVersion());

         if (remoteService == null) {
            String msg = String.format("Target Service '%s' not found in control plane", serviceRef.getName());
            logger.warnf("Resource '%s': %s", name, msg);
            updateStatus(resource, null, null, Status.ERROR, msg);
            return UpdateControl.patchStatus(resource).rescheduleAfter(RETRY_DELAY_MS);
         }

         String content = getArtifactContent(resource);
         String artifactName = getArtifactName(resource);
         ArtifactType type = getArtifactType();

         Artifact attached = artifactAttachClient.attachArtifact(apiClient, remoteService.getId(), artifactName, type, content, false);

         if (attached != null) {
            logger.infof("Successfully attached %s artifact id=%s to service id=%s for resource '%s'", type, attached.getId(), remoteService.getId(), name);
            updateStatus(resource, remoteService.getId(), attached.getId(), Status.READY, "Artifact successfully synchronized");
            return UpdateControl.patchStatus(resource);
         } else {
            String msg = "Artifact attach endpoint returned empty response";
            logger.warnf("Resource '%s': %s", name, msg);
            updateStatus(resource, remoteService.getId(), null, Status.ERROR, msg);
            return UpdateControl.patchStatus(resource).rescheduleAfter(RETRY_DELAY_MS);
         }

      } catch (ApiException e) {
         String msg = "Control plane API error: " + safeMessage(e);
         logger.errorf(e, "Error attaching artifact for resource '%s'", name);
         updateStatus(resource, null, null, Status.ERROR, msg);
         return UpdateControl.patchStatus(resource).rescheduleAfter(RETRY_DELAY_MS);
      } catch (Exception e) {
         String msg = "Internal error: " + safeMessage(e);
         logger.errorf(e, "Internal error processing resource '%s'", name);
         updateStatus(resource, null, null, Status.ERROR, msg);
         return UpdateControl.patchStatus(resource).rescheduleAfter(RETRY_DELAY_MS);
      }
   }
}
