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

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.kubernetes.api.gatewaygroup.v1alpha1.GatewayGroup;
import io.reshapr.kubernetes.api.gatewaygroup.v1alpha1.GatewayGroupSpec;
import io.reshapr.kubernetes.api.gatewaygroup.v1alpha1.GatewayGroupStatus;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

/**
 * Reconciler for GatewayGroup custom resource.
 * <p>
 * Keeps a reShapr {@link GatewayGroup} custom resource in sync with its counterpart
 * in the control plane. On reconciliation it looks up the matching control plane
 * GatewayGroup (by name); when none exists it creates one, then records its
 * identifier in the resource status. When the spec is updated, the control plane
 * GatewayGroup is updated in place (name and labels). On deletion it removes the
 * control plane GatewayGroup unless the {@code keepOnDelete} flag is set.
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class GatewayGroupReconciler extends BaseReshaprReconciler<GatewayGroup> implements Cleaner<GatewayGroup> {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   public GatewayGroupReconciler(ReshaprApiClientFactory apiClientFactory) {
      super(apiClientFactory);
   }

   @Override
   protected UpdateControl<GatewayGroup> doReconcile(GatewayGroup gatewayGroup, Context<GatewayGroup> context, ApiClient apiClient) {
      DefaultApi api = new DefaultApi(apiClient);
      GatewayGroupSpec spec = gatewayGroup.getSpec();
      String desiredName = resolveName(gatewayGroup, spec);

      GatewayGroupStatus status = gatewayGroup.getStatus();
      if (status == null) {
         status = new GatewayGroupStatus();
      }

      Long generation = gatewayGroup.getMetadata().getGeneration();
      String currentGatewayGroupId = status.getGatewayGroupId();

      // The GatewayGroup is considered already created if we recorded its control plane id.
      boolean alreadyCreated = currentGatewayGroupId != null && !currentGatewayGroupId.isBlank();
      // The spec changed if the observed generation no longer matches the resource generation.
      boolean specChanged = generation == null || generation != status.getObservedGeneration();

      if (alreadyCreated && !specChanged) {
         logger.debugf("GatewayGroup '%s' already created (id=%s) and spec unchanged (generation=%s) — nothing to do",
               desiredName, currentGatewayGroupId, generation);
         return UpdateControl.noUpdate();
      }

      String organization = gatewayGroup.getMetadata().getAnnotations().get(ReshaprAnnotations.ORGANIZATION);
      Map<String, Object> desiredLabels = toObjectLabels(spec != null ? spec.getLabels() : null);

      try {
         String newGatewayGroupId;

         if (alreadyCreated) {
            // Update the existing control plane GatewayGroup in place.
            io.reshapr.client.model.GatewayGroup remote = new io.reshapr.client.model.GatewayGroup();
            remote.setId(currentGatewayGroupId);
            remote.setOrganizationId(organization);
            remote.setName(desiredName);
            remote.setLabels(desiredLabels);
            try {
               io.reshapr.client.model.GatewayGroup updated = api.updateGatewayGroup(currentGatewayGroupId, remote);
               newGatewayGroupId = updated != null && updated.getId() != null ? updated.getId() : currentGatewayGroupId;
               logger.infof("GatewayGroup '%s' updated in control plane with id=%s", desiredName, newGatewayGroupId);
            } catch (ApiException e) {
               if (e.getCode() == 404) {
                  // Recorded id no longer exists in control plane — fall through to (re)create.
                  logger.warnf("Recorded control plane GatewayGroup id=%s for '%s' no longer exists (HTTP 404) — recreating",
                        currentGatewayGroupId, desiredName);
                  newGatewayGroupId = createRemoteGatewayGroup(api, organization, desiredName, desiredLabels);
               } else {
                  throw e;
               }
            }
         } else {
            // First reconciliation — try to adopt an existing remote GatewayGroup with the same name,
            // otherwise create a new one.
            io.reshapr.client.model.GatewayGroup existing = findRemoteGatewayGroup(api, desiredName);
            if (existing != null) {
               logger.infof("Adopting existing control plane GatewayGroup '%s' with id=%s", desiredName, existing.getId());
               existing.setLabels(desiredLabels);
               io.reshapr.client.model.GatewayGroup updated = api.updateGatewayGroup(existing.getId(), existing);
               newGatewayGroupId = updated != null && updated.getId() != null ? updated.getId() : existing.getId();
            } else {
               newGatewayGroupId = createRemoteGatewayGroup(api, organization, desiredName, desiredLabels);
            }
         }

         status.setStatus(Status.READY);
         status.setMessage(null);
         status.setGatewayGroupId(newGatewayGroupId);
         if (generation != null) {
            status.setObservedGeneration(generation);
         }
         gatewayGroup.setStatus(status);
         return UpdateControl.patchStatus(gatewayGroup);
      } catch (ApiException e) {
         logger.errorf(e, "Control plane error while reconciling GatewayGroup '%s' — rescheduling", desiredName);
         return recordStatus(gatewayGroup, Status.ERROR,
               "Control plane error: " + e.getCode() + " " + safeMessage(e)).rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /** Surface a reconciliation status (and message) on the {@link GatewayGroup} custom resource. */
   @Override
   protected UpdateControl<GatewayGroup> recordStatus(GatewayGroup gatewayGroup, Status status, String message) {
      GatewayGroupStatus gatewayGroupStatus = gatewayGroup.getStatus();
      if (gatewayGroupStatus == null) {
         gatewayGroupStatus = new GatewayGroupStatus();
      }
      gatewayGroupStatus.setStatus(status);
      gatewayGroupStatus.setMessage(message);
      gatewayGroup.setStatus(gatewayGroupStatus);
      return UpdateControl.patchStatus(gatewayGroup);
   }

   @Override
   public DeleteControl cleanup(GatewayGroup gatewayGroup, Context<GatewayGroup> context) {
      String name = gatewayGroup.getMetadata().getName();
      GatewayGroupSpec spec = gatewayGroup.getSpec();

      if (spec != null && spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set for GatewayGroup '%s' — leaving control plane GatewayGroup in place", name);
         return DeleteControl.defaultDelete();
      }

      try {
         ApiClient apiClient = authenticatedClientFor(gatewayGroup);
         if (apiClient == null) {
            logger.warnf("Missing reShapr annotations on GatewayGroup '%s' — cannot delete control plane GatewayGroup, removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         DefaultApi api = new DefaultApi(apiClient);

         String gatewayGroupId = gatewayGroup.getStatus() != null ? gatewayGroup.getStatus().getGatewayGroupId() : null;
         if (gatewayGroupId == null) {
            io.reshapr.client.model.GatewayGroup remote = findRemoteGatewayGroup(api, resolveName(gatewayGroup, spec));
            gatewayGroupId = remote != null ? remote.getId() : null;
         }

         if (gatewayGroupId != null) {
            api.deleteGateGroup(gatewayGroupId);
            logger.infof("Deleted control plane GatewayGroup id=%s for resource '%s'", gatewayGroupId, name);
         } else {
            logger.infof("No control plane GatewayGroup to delete for resource '%s'", name);
         }
         return DeleteControl.defaultDelete();
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed while deleting control plane GatewayGroup for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      } catch (ApiException e) {
         // A 404 means the control plane GatewayGroup is already gone: nothing to retry, just finish cleanup.
         if (e.getCode() == 404) {
            logger.infof("Control plane GatewayGroup for '%s' already deleted (HTTP 404) — removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         logger.errorf(e, "Control plane error while deleting GatewayGroup for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /** Create a fresh GatewayGroup in the control plane and return its identifier. */
   private String createRemoteGatewayGroup(DefaultApi api, String organization, String desiredName,
         Map<String, Object> labels) throws ApiException {
      io.reshapr.client.model.GatewayGroup remote = new io.reshapr.client.model.GatewayGroup();
      remote.setOrganizationId(organization);
      remote.setName(desiredName);
      remote.setLabels(labels);
      io.reshapr.client.model.GatewayGroup created = api.createGatewayGroup(remote);
      String id = created != null ? created.getId() : null;
      logger.infof("GatewayGroup '%s' created in control plane with id=%s", desiredName, id);
      return id;
   }

   /**
    * Find a control plane GatewayGroup matching the given name.
    * @return the matching {@link io.reshapr.client.model.GatewayGroup}, or {@code null} if none matches.
    */
   private io.reshapr.client.model.GatewayGroup findRemoteGatewayGroup(DefaultApi api, String name) throws ApiException {
      List<io.reshapr.client.model.GatewayGroup> all = api.listGatewayGroups();
      if (all == null) {
         return null;
      }
      for (io.reshapr.client.model.GatewayGroup candidate : all) {
         if (name.equals(candidate.getName())) {
            return candidate;
         }
      }
      return null;
   }

   /**
    * Resolve the control plane GatewayGroup name from the spec override or fall back
    * to the custom resource metadata name.
    */
   private String resolveName(GatewayGroup gatewayGroup, GatewayGroupSpec spec) {
      if (spec != null && spec.getName() != null && !spec.getName().isBlank()) {
         return spec.getName();
      }
      return gatewayGroup.getMetadata().getName();
   }

   /** Convert the CR string label map into the client model's {@code Map<String, Object>}. */
   private Map<String, Object> toObjectLabels(Map<String, String> labels) {
      if (labels == null || labels.isEmpty()) {
         return null;
      }
      Map<String, Object> out = new HashMap<>(labels.size());
      out.putAll(labels);
      return out;
   }
}
