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
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.service.v1alpha1.Service;
import io.reshapr.kubernetes.api.service.v1alpha1.ServiceSpec;
import io.reshapr.kubernetes.api.service.v1alpha1.ServiceStatus;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;
import io.reshapr.kubernetes.operator.client.ArtifactImportClient;
import io.reshapr.kubernetes.operator.client.ArtifactImportRequest;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;
/**
 * Reconciler for Service custom resource.
 * <p>
 * Keeps a reShapr {@link Service} custom resource in sync with its counterpart in
 * the control plane. On reconciliation it looks up the matching control plane
 * Service (by name and, when provided, version); when none exists and a spec
 * {@code url} is provided, it imports the artifact from that URL to create the
 * Service, then records its identifier in the resource status. On deletion it
 * removes the control plane Service unless the {@code keepOnDelete} flag is set.
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class ServiceReconciler extends BaseReshaprReconciler<Service> implements Cleaner<Service> {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** Page size used when paginating control plane Services. */
   private static final int PAGE_SIZE = 50;

   /** Hand-written client to import an Artifact from a remote URL (form-urlencoded). */
   private final ArtifactImportClient artifactImportClient;

   @Inject
   public ServiceReconciler(ReshaprApiClientFactory apiClientFactory, ArtifactImportClient artifactImportClient) {
      super(apiClientFactory);
      this.artifactImportClient = artifactImportClient;
   }

   @Override
   protected UpdateControl<Service> doReconcile(Service service, Context<Service> context, ApiClient apiClient) {
      DefaultApi api = new DefaultApi(apiClient);
      ServiceSpec spec = service.getSpec();
      String desiredName = resolveName(service, spec);

      ServiceStatus status = service.getStatus();
      if (status == null) {
         status = new ServiceStatus();
      }

      Long generation = service.getMetadata().getGeneration();
      String currentServiceId = status.getServiceId();

      // The Service is considered already imported if we recorded its control plane id.
      boolean alreadyImported = currentServiceId != null && !currentServiceId.isBlank();
      // The spec changed if the observed generation no longer matches the resource generation.
      boolean specChanged = generation == null || generation != status.getObservedGeneration();

      // (Re-)import is required when never imported yet, or when the spec has been updated.
      if (alreadyImported && !specChanged) {
         logger.debugf("Service '%s' already imported (id=%s) and spec unchanged (generation=%s) — nothing to do",
               desiredName, currentServiceId, generation);
         return UpdateControl.noUpdate();
      }

      if (spec == null || spec.getUrl() == null || spec.getUrl().isBlank()) {
         logger.warnf("Service '%s' (%s/%s) has no spec.url — cannot import into control plane, skipping",
               desiredName, service.getMetadata().getNamespace(), service.getMetadata().getName());
         return recordStatus(service, Status.ERROR, "No spec.url provided — cannot import into control plane");
      }

      try {
         // A changed name or version produces a brand new control plane Service (new id),
         // hence we always resolve and refresh status.serviceId after the import.
         io.reshapr.client.model.Service imported = importService(apiClient, spec, desiredName);

         if (imported == null) {
            logger.warnf("Import of Service '%s' did not yield a resolvable control plane Service — will retry",
                  desiredName);
            return recordStatus(service, Status.IN_PROGRESS,
                  "Import of Service did not yield a resolvable control plane Service").rescheduleAfter(RETRY_DELAY_MS);
         }

         logger.infof("Service '%s' (version '%s') imported in control plane with id=%s",
               imported.getName(), imported.getVersion(), imported.getId());
         status.setStatus(Status.READY);
         status.setMessage(null);

         // A changed name/version yields a new control plane Service: remove the previous one.
         deletePreviousServiceIfReplaced(api, spec, alreadyImported, currentServiceId, imported.getId(), desiredName);

         status.setServiceId(imported.getId());
         if (generation != null) {
            status.setObservedGeneration(generation);
         }
         service.setStatus(status);
         return UpdateControl.patchStatus(service);
      } catch (ApiException e) {
         logger.errorf(e, "Control plane error while importing Service '%s' — rescheduling", desiredName);
         return recordStatus(service, Status.ERROR,
               "Control plane error: " + e.getCode() + " " + safeMessage(e)).rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /** Surface a reconciliation status (and message) on the {@link Service} custom resource. */
   @Override
   protected UpdateControl<Service> recordStatus(Service service, Status status, String message) {
      ServiceStatus serviceStatus = service.getStatus();
      if (serviceStatus == null) {
         serviceStatus = new ServiceStatus();
      }
      serviceStatus.setStatus(status);
      serviceStatus.setMessage(message);
      service.setStatus(serviceStatus);
      return UpdateControl.patchStatus(service);
   }

   @Override
   public DeleteControl cleanup(Service service, Context<Service> context) {
      String name = service.getMetadata().getName();
      ServiceSpec spec = service.getSpec();

      if (spec != null && spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set for Service '%s' — leaving control plane Service in place", name);
         return DeleteControl.defaultDelete();
      }

      try {
         ApiClient apiClient = authenticatedClientFor(service);
         if (apiClient == null) {
            logger.warnf("Missing reShapr annotations on Service '%s' — cannot delete control plane Service, removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         DefaultApi api = new DefaultApi(apiClient);

         String serviceId = service.getStatus() != null ? service.getStatus().getServiceId() : null;
         if (serviceId == null) {
            io.reshapr.client.model.Service remote =
                  findRemoteService(api, resolveName(service, spec), spec != null ? spec.getVersion() : null);
            serviceId = remote != null ? remote.getId() : null;
         }

         if (serviceId != null) {
            api.deleteService(serviceId);
            logger.infof("Deleted control plane Service id=%s for resource '%s'", serviceId, name);
         } else {
            logger.infof("No control plane Service to delete for resource '%s'", name);
         }
         return DeleteControl.defaultDelete();
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed while deleting control plane Service for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      } catch (ApiException e) {
         // A 404 means the control plane Service is already gone: nothing to retry, just finish cleanup.
         if (e.getCode() == 404) {
            logger.infof("Control plane Service for '%s' already deleted (HTTP 404) — removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         logger.errorf(e, "Control plane error while deleting Service for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /**
    * Import the Service artifact from the spec URL and return the resulting control
    * plane Service (resolved by name/version after import), or {@code null} if it
    * could not be resolved afterwards.
    */
   private io.reshapr.client.model.Service importService(ApiClient apiClient, ServiceSpec spec, String desiredName)
         throws ApiException {
      logger.infof("Importing Service '%s' in control plane from URL %s", desiredName, spec.getUrl());

      ArtifactImportRequest request = ArtifactImportRequest.builder()
            .url(spec.getUrl())
            .mainArtifact(Boolean.TRUE)
            .secretName(spec.getSecretRef())
            .serviceName(desiredName)
            .serviceVersion(spec.getVersion())
            .includedOperations(spec.getIncludedOperations())
            .excludedOperations(spec.getExcludedOperations())
            .build();

      io.reshapr.client.model.Service service = artifactImportClient.importArtifactFromUrl(apiClient, request);
      if (service != null) {
         logger.infof("Discovered service id=%s for Service '%s:%s'",
               service.getId(), service.getName(), service.getVersion());
      }
      // The import returns a Service; return the resulting Service.
      return service;
   }

   /**
    * Best-effort deletion of a previous control plane Service that became orphaned after
    * a re-import produced a new id. Failures are logged but do not fail the reconciliation,
    * so the freshly imported id can still be recorded in the status.
    */
   private void deleteOrphanService(DefaultApi api, String serviceId, String name) {
      try {
         api.deleteService(serviceId);
         logger.infof("Deleted previous control plane Service id=%s for resource '%s' after re-import",
               serviceId, name);
      } catch (ApiException e) {
         // A 404 means the previous Service was already gone — no orphan to worry about.
         if (e.getCode() == 404) {
            logger.infof("Previous control plane Service id=%s for '%s' already deleted (HTTP 404)",
                  serviceId, name);
         } else {
            logger.warnf(e, "Failed to delete previous control plane Service id=%s for '%s' — possible orphan",
                  serviceId, name);
         }
      }
   }

   /**
    * When a re-import yields a different control plane Service id, delete the previous one to
    * avoid orphans — unless {@code keepOnDelete} asks to preserve it. No-op on the first import
    * or when the id is unchanged.
    */
   private void deletePreviousServiceIfReplaced(DefaultApi api, ServiceSpec spec, boolean alreadyImported,
         String previousServiceId, String newServiceId, String desiredName) {
      if (!alreadyImported || newServiceId.equals(previousServiceId)) {
         return;
      }
      if (spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set — keeping previous control plane Service id=%s for '%s'",
               previousServiceId, desiredName);
      } else {
         deleteOrphanService(api, previousServiceId, desiredName);
      }
   }

   /**
    * Resolve the control plane Service name from the spec override or fall back to
    * the custom resource metadata name.
    */
   private String resolveName(Service service, ServiceSpec spec) {
      if (spec != null && spec.getName() != null && !spec.getName().isBlank()) {
         return spec.getName();
      }
      return service.getMetadata().getName();
   }


}
