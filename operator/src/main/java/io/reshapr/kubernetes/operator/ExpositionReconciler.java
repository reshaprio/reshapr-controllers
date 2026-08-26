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
import io.reshapr.client.model.ConfigurationPlan;
import io.reshapr.client.model.ExpositionReference;
import io.reshapr.client.model.GatewayGroup;
import io.reshapr.kubernetes.api.exposition.v1alpha1.Exposition;
import io.reshapr.kubernetes.api.exposition.v1alpha1.ExpositionSpec;
import io.reshapr.kubernetes.api.exposition.v1alpha1.ExpositionStatus;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

/**
 * Reconciler for Exposition custom resource.
 * <p>
 * Keeps a reShapr {@link Exposition} custom resource in sync with its counterpart in
 * the control plane. On reconciliation it resolves the referenced Service (by name
 * and optional version), ConfigurationPlan (by name, scoped to the resolved Service —
 * plan names are not globally unique) and GatewayGroup (by name), then creates an
 * Exposition in the control plane linking them — using the custom resource
 * {@code metadata.name} as the control plane Exposition name — and records its
 * identifier in the resource status. When the spec is updated, the previous control
 * plane Exposition is deleted and a new one is created (the public API does not offer
 * an update endpoint on {@code /v1/expositions/{id}}). On deletion, it removes the
 * control plane Exposition unless the {@code keepOnDelete} flag is set.
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class ExpositionReconciler extends BaseReshaprReconciler<Exposition> implements Cleaner<Exposition> {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   public ExpositionReconciler(ReshaprApiClientFactory apiClientFactory) {
      super(apiClientFactory);
   }

   @Override
   protected UpdateControl<Exposition> doReconcile(Exposition exposition, Context<Exposition> context, ApiClient apiClient) {
      DefaultApi api = new DefaultApi(apiClient);
      ExpositionSpec spec = exposition.getSpec();
      String name = exposition.getMetadata().getName();

      ExpositionStatus status = exposition.getStatus();
      if (status == null) {
         status = new ExpositionStatus();
      }

      Long generation = exposition.getMetadata().getGeneration();
      String currentExpositionId = status.getExpositionId();

      // The Exposition is considered already created if we recorded its control plane id.
      boolean alreadyCreated = currentExpositionId != null && !currentExpositionId.isBlank();
      // The spec changed if the observed generation no longer matches the resource generation.
      boolean specChanged = generation == null || generation != status.getObservedGeneration();

      if (alreadyCreated && !specChanged) {
         logger.debugf("Exposition '%s' already created (id=%s) and spec unchanged (generation=%s) — nothing to do",
               name, currentExpositionId, generation);
         return UpdateControl.noUpdate();
      }

      String missing = validateSpec(spec);
      if (missing != null) {
         logger.warnf("Exposition '%s' has invalid spec — %s", name, missing);
         return recordStatus(exposition, Status.ERROR, missing);
      }

      try {
         // Resolve the referenced Service by name (and optionally version) — its id is
         // needed to scope the ConfigurationPlan lookup since plan names are not globally unique.
         ServiceRef serviceRef = spec.getService();
         io.reshapr.client.model.Service remoteService = findRemoteService(api, serviceRef.getName(), serviceRef.getVersion());
         if (remoteService == null) {
            logger.warnf("Exposition '%s': target Service '%s' (version '%s') not found in control plane — rescheduling",
                  name, serviceRef.getName(), serviceRef.getVersion());
            return recordStatus(exposition, Status.IN_PROGRESS,
                  "Target Service '" + serviceRef.getName() + "' not found in control plane")
                  .rescheduleAfter(RETRY_DELAY_MS);
         }

         // Resolve the referenced ConfigurationPlan by name, scoped to this Service.
         ConfigurationPlan remotePlan = findRemoteConfigurationPlan(api, remoteService.getId(), spec.getConfigurationPlan());
         if (remotePlan == null) {
            logger.warnf("Exposition '%s': ConfigurationPlan '%s' not found for Service id=%s — rescheduling",
                  name, spec.getConfigurationPlan(), remoteService.getId());
            return recordStatus(exposition, Status.IN_PROGRESS,
                  "ConfigurationPlan '" + spec.getConfigurationPlan() + "' not found for target Service")
                  .rescheduleAfter(RETRY_DELAY_MS);
         }

         // Resolve the referenced GatewayGroup by name.
         GatewayGroup remoteGroup = findRemoteGatewayGroup(api, spec.getGatewayGroup());
         if (remoteGroup == null) {
            logger.warnf("Exposition '%s': GatewayGroup '%s' not found in control plane — rescheduling",
                  name, spec.getGatewayGroup());
            return recordStatus(exposition, Status.IN_PROGRESS,
                  "GatewayGroup '" + spec.getGatewayGroup() + "' not found in control plane")
                  .rescheduleAfter(RETRY_DELAY_MS);
         }

         // Build the ExpositionReference payload, using the custom resource metadata.name
         // as the organization-unique Exposition name in the control plane.
         String organization = exposition.getMetadata().getAnnotations().get(ReshaprAnnotations.ORGANIZATION);
         ExpositionReference reference = new ExpositionReference();
         reference.setOrganizationId(organization);
         reference.setName(name);
         reference.setConfigurationPlanId(remotePlan.getId());
         reference.setGatewayGroupId(remoteGroup.getId());

         io.reshapr.client.model.Exposition created = api.createExposition(reference);
         String newExpositionId = created != null ? created.getId() : null;

         if (newExpositionId == null) {
            logger.warnf("Exposition '%s': control plane creation did not return an id — rescheduling", name);
            return recordStatus(exposition, Status.IN_PROGRESS,
                  "Control plane did not return an Exposition id").rescheduleAfter(RETRY_DELAY_MS);
         }

         logger.infof("Exposition '%s' created in control plane with id=%s (service=%s, configurationPlan=%s, gatewayGroup=%s)",
               name, newExpositionId, remoteService.getId(), remotePlan.getId(), remoteGroup.getId());

         // A spec change yields a new control plane Exposition — remove the previous one.
         deletePreviousExpositionIfReplaced(api, spec, alreadyCreated, currentExpositionId, newExpositionId, name);

         status.setStatus(Status.READY);
         status.setMessage(null);
         status.setExpositionId(newExpositionId);
         if (generation != null) {
            status.setObservedGeneration(generation);
         }
         exposition.setStatus(status);
         return UpdateControl.patchStatus(exposition);
      } catch (ApiException e) {
         logger.errorf(e, "Control plane error while reconciling Exposition '%s' — rescheduling", name);
         return recordStatus(exposition, Status.ERROR,
               "Control plane error: " + e.getCode() + " " + safeMessage(e)).rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /** Surface a reconciliation status (and message) on the {@link Exposition} custom resource. */
   @Override
   protected UpdateControl<Exposition> recordStatus(Exposition exposition, Status status, String message) {
      ExpositionStatus expositionStatus = exposition.getStatus();
      if (expositionStatus == null) {
         expositionStatus = new ExpositionStatus();
      }
      expositionStatus.setStatus(status);
      expositionStatus.setMessage(message);
      exposition.setStatus(expositionStatus);
      return UpdateControl.patchStatus(exposition);
   }

   @Override
   public DeleteControl cleanup(Exposition exposition, Context<Exposition> context) {
      String name = exposition.getMetadata().getName();
      ExpositionSpec spec = exposition.getSpec();

      if (spec != null && spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set for Exposition '%s' — leaving control plane Exposition in place", name);
         return DeleteControl.defaultDelete();
      }

      try {
         ApiClient apiClient = authenticatedClientFor(exposition);
         if (apiClient == null) {
            logger.warnf("Missing reShapr annotations on Exposition '%s' — cannot delete control plane Exposition, removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         DefaultApi api = new DefaultApi(apiClient);

         String expositionId = exposition.getStatus() != null ? exposition.getStatus().getExpositionId() : null;
         if (expositionId != null) {
            api.deleteExposition(expositionId);
            logger.infof("Deleted control plane Exposition id=%s for resource '%s'", expositionId, name);
         } else {
            logger.infof("No control plane Exposition to delete for resource '%s'", name);
         }
         return DeleteControl.defaultDelete();
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed while deleting control plane Exposition for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      } catch (ApiException e) {
         // A 404 means the control plane Exposition is already gone: nothing to retry, just finish cleanup.
         if (e.getCode() == 404) {
            logger.infof("Control plane Exposition for '%s' already deleted (HTTP 404) — removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         logger.errorf(e, "Control plane error while deleting Exposition for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /**
    * Validate the spec has the mandatory fields required to create an Exposition.
    * @return a human-readable error message, or {@code null} if the spec is valid.
    */
   private String validateSpec(ExpositionSpec spec) {
      if (spec == null) {
         return "Missing spec";
      }
      if (spec.getService() == null || spec.getService().getName() == null || spec.getService().getName().isBlank()) {
         return "Missing spec.service.name";
      }
      if (spec.getConfigurationPlan() == null || spec.getConfigurationPlan().isBlank()) {
         return "Missing spec.configurationPlan";
      }
      if (spec.getGatewayGroup() == null || spec.getGatewayGroup().isBlank()) {
         return "Missing spec.gatewayGroup";
      }
      return null;
   }

   /** Find a control plane ConfigurationPlan attached to the given service by name. */
   private ConfigurationPlan findRemoteConfigurationPlan(DefaultApi api, String serviceId, String planName) throws ApiException {
      List<ConfigurationPlan> plans = api.getConfigPlans(serviceId);
      if (plans == null) {
         return null;
      }
      for (ConfigurationPlan candidate : plans) {
         if (planName.equals(candidate.getName())) {
            return candidate;
         }
      }
      return null;
   }

   /** Find a control plane GatewayGroup by name. */
   private GatewayGroup findRemoteGatewayGroup(DefaultApi api, String name) throws ApiException {
      List<GatewayGroup> all = api.listGatewayGroups();
      if (all == null) {
         return null;
      }
      for (GatewayGroup candidate : all) {
         if (name.equals(candidate.getName())) {
            return candidate;
         }
      }
      return null;
   }

   /**
    * When a spec change requires creating a new control plane Exposition, delete the previous
    * one to avoid orphans — unless {@code keepOnDelete} asks to preserve it. No-op on the first
    * creation or when the id is unchanged.
    */
   private void deletePreviousExpositionIfReplaced(DefaultApi api, ExpositionSpec spec, boolean alreadyCreated,
         String previousExpositionId, String newExpositionId, String name) {
      if (!alreadyCreated || previousExpositionId == null || previousExpositionId.equals(newExpositionId)) {
         return;
      }
      if (spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set — keeping previous control plane Exposition id=%s for '%s'",
               previousExpositionId, name);
         return;
      }
      try {
         api.deleteExposition(previousExpositionId);
         logger.infof("Deleted previous control plane Exposition id=%s for resource '%s' after re-creation",
               previousExpositionId, name);
      } catch (ApiException e) {
         if (e.getCode() == 404) {
            logger.infof("Previous control plane Exposition id=%s for '%s' already deleted (HTTP 404)",
                  previousExpositionId, name);
         } else {
            logger.warnf(e, "Failed to delete previous control plane Exposition id=%s for '%s' — possible orphan",
                  previousExpositionId, name);
         }
      }
   }
}
