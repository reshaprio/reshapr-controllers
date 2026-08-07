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
import io.reshapr.client.model.OAuth2ClientConfiguration;
import io.reshapr.client.model.Secret;
import io.reshapr.client.model.SecretType;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.ConfigurationPlan;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.ConfigurationPlanSpec;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.ConfigurationPlanStatus;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.OAuth2Spec;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

/**
 * Reconciler for ConfigurationPlan custom resource.
 * Keeps a reShapr {@link ConfigurationPlan} custom resource in sync with its counterpart in
 * the control plane.
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@ApplicationScoped
public class ConfigurationPlanReconciler extends BaseReshaprReconciler<ConfigurationPlan> implements Cleaner<ConfigurationPlan> {

   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   public ConfigurationPlanReconciler(ReshaprApiClientFactory apiClientFactory) {
      super(apiClientFactory);
   }

   @Override
   protected UpdateControl<ConfigurationPlan> doReconcile(ConfigurationPlan resource, Context<ConfigurationPlan> context, ApiClient apiClient) {
      DefaultApi api = new DefaultApi(apiClient);
      ConfigurationPlanSpec spec = resource.getSpec();
      String name = resource.getMetadata().getName();

      ConfigurationPlanStatus status = resource.getStatus();
      if (status == null) {
         status = new ConfigurationPlanStatus();
      }

      Long generation = resource.getMetadata().getGeneration();
      String currentPlanId = status.getConfigurationPlanId();

      boolean alreadyImported = currentPlanId != null && !currentPlanId.isBlank();
      boolean specChanged = generation == null || generation != status.getObservedGeneration();

      if (alreadyImported && !specChanged) {
         logger.debugf("ConfigurationPlan '%s' already created (id=%s) and spec unchanged — nothing to do", name, currentPlanId);
         return UpdateControl.noUpdate();
      }

      if (spec == null || spec.getService() == null || spec.getService().getName() == null) {
         logger.warnf("ConfigurationPlan '%s' has no spec.service.name — cannot reconcile", name);
         return recordStatus(resource, Status.ERROR, "No spec.service.name provided");
      }

      try {
         io.reshapr.client.model.Service remoteService = findRemoteService(api, spec.getService().getName(), spec.getService().getVersion());
         if (remoteService == null) {
            return recordStatus(resource, Status.IN_PROGRESS, "Target Service not found in control plane").rescheduleAfter(RETRY_DELAY_MS);
         }

         String backendSecretId = null;
         String organization = resource.getMetadata().getAnnotations().get(ReshaprAnnotations.ORGANIZATION);

         if (spec.getOauth2() != null) {
            OAuth2Spec oauth2 = spec.getOauth2();
            Secret secret = new Secret();
            secret.setOrganizationId(organization);
            secret.setName(name + "-oauth2");
            secret.setType(SecretType.ENDPOINT);
            
            OAuth2ClientConfiguration oauth2Config = new OAuth2ClientConfiguration();
            oauth2Config.setClientId(oauth2.getClientId());
            oauth2Config.setClientSecret(oauth2.getClientSecret());
            oauth2Config.setAuthorizationEndpoint(""); // Defaulting
            oauth2Config.setTokenEndpoint(""); // Defaulting
            secret.setOauth2ClientConfiguration(oauth2Config);
            
            Secret createdSecret = api.createSecret(secret);
            backendSecretId = (String) createdSecret.getId();
         }

         io.reshapr.client.model.ConfigurationPlan remotePlan = new io.reshapr.client.model.ConfigurationPlan();
         remotePlan.setOrganizationId(organization);
         remotePlan.setName(name);
         remotePlan.setServiceId(remoteService.getId());
         remotePlan.setBackendEndpoint(spec.getBackendEndpoint() != null ? spec.getBackendEndpoint() : "");
         
         if (backendSecretId != null) {
            remotePlan.setBackendSecretId(backendSecretId);
         }

         io.reshapr.client.model.ConfigurationPlan createdPlan = api.createConfigPlan(remotePlan);
         String newPlanId = createdPlan.getId();

         if (spec.isApiKey()) {
            api.renewConfigPlanApiKey(newPlanId);
         }

         if (alreadyImported && !newPlanId.equals(currentPlanId)) {
             try {
                 api.deleteConfigPlan(currentPlanId);
             } catch (ApiException e) {
                 if (e.getCode() != 404) {
                     logger.warnf(e, "Failed to delete previous ConfigurationPlan id=%s", currentPlanId);
                 }
             }
         }

         status.setStatus(Status.READY);
         status.setMessage(null);
         status.setConfigurationPlanId(newPlanId);
         status.setObservedGeneration(generation);
         resource.setStatus(status);

         return UpdateControl.patchStatus(resource);
      } catch (ApiException e) {
         logger.errorf(e, "Control plane error while reconciling ConfigurationPlan '%s'", name);
         return recordStatus(resource, Status.ERROR, "Control plane error: " + e.getCode() + " " + safeMessage(e)).rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   @Override
   protected UpdateControl<ConfigurationPlan> recordStatus(ConfigurationPlan resource, Status status, String message) {
      ConfigurationPlanStatus currentStatus = resource.getStatus();
      if (currentStatus == null) {
         currentStatus = new ConfigurationPlanStatus();
      }
      currentStatus.setStatus(status);
      currentStatus.setMessage(message);
      resource.setStatus(currentStatus);
      return UpdateControl.patchStatus(resource);
   }

   @Override
   public DeleteControl cleanup(ConfigurationPlan resource, Context<ConfigurationPlan> context) {
      String name = resource.getMetadata().getName();
      try {
         ApiClient apiClient = authenticatedClientFor(resource);
         if (apiClient == null) {
            return DeleteControl.defaultDelete();
         }
         DefaultApi api = new DefaultApi(apiClient);

         String planId = resource.getStatus() != null ? resource.getStatus().getConfigurationPlanId() : null;
         if (planId != null) {
            api.deleteConfigPlan(planId);
            logger.infof("Deleted control plane ConfigurationPlan id=%s for resource '%s'", planId, name);
         }
         return DeleteControl.defaultDelete();
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed while deleting control plane ConfigurationPlan for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      } catch (ApiException e) {
         if (e.getCode() == 404) {
            return DeleteControl.defaultDelete();
         }
         logger.errorf(e, "Control plane error while deleting ConfigurationPlan for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      }
   }
}
