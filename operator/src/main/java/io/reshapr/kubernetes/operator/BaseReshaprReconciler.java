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
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.ApiException;
/**
 * Abstract base reconciler providing authentication and API client setup for
 * all reShapr custom resource reconcilers.
 * Each reconciliation cycle extracts the {@code reshapr.io/instance} and
 * {@code reshapr.io/organization} annotations from the custom resource,
 * authenticates with the control plane using the projected service account
 * token, and delegates to the concrete {@link #doReconcile} method with an
 * authenticated {@link ApiClient}.
 *
 * @param <R> The custom resource type being reconciled.
 * @author laurent
 */
public abstract class BaseReshaprReconciler<R extends CustomResource<?, ?>> implements Reconciler<R> {

   private static final Logger logger = Logger.getLogger(BaseReshaprReconciler.class);

   /** Default delay before retrying after a recoverable failure (e.g. authentication), in milliseconds. */
   protected static final long RETRY_DELAY_MS = 30_000L;

   /** Maximum length of an error message persisted into the CR status. */
   private static final int MAX_STATUS_MESSAGE_LENGTH = 256;

   private ReshaprApiClientFactory apiClientFactory;

   /** No-args constructor required by CDI for proxying. */
   BaseReshaprReconciler() {
   }

   protected BaseReshaprReconciler(ReshaprApiClientFactory apiClientFactory) {
      this.apiClientFactory = apiClientFactory;
   }

   @Override
   public final UpdateControl<R> reconcile(R resource, Context<R> context) {
      String namespace = resource.getMetadata().getNamespace();
      String name = resource.getMetadata().getName();
      Map<String, String> annotations = resource.getMetadata().getAnnotations();

      String instance = annotations != null ? annotations.get(ReshaprAnnotations.INSTANCE) : null;
      String organization = annotations != null ? annotations.get(ReshaprAnnotations.ORGANIZATION) : null;

      if (instance == null || instance.isBlank()) {
         String message = "Missing required annotation '" + ReshaprAnnotations.INSTANCE + "'";
         logger.warnf("Resource %s/%s: %s — skipping reconciliation", namespace, name, message);
         return recordStatus(resource, Status.ERROR, message);
      }
      if (organization == null || organization.isBlank()) {
         String message = "Missing required annotation '" + ReshaprAnnotations.ORGANIZATION + "'";
         logger.warnf("Resource %s/%s: %s — skipping reconciliation", namespace, name, message);
         return recordStatus(resource, Status.ERROR, message);
      }

      try {
         ApiClient apiClient = apiClientFactory.createAuthenticatedApiClient(instance, organization);
         return doReconcile(resource, context, apiClient);
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed for resource %s/%s targeting instance=%s, organization=%s",
               namespace, name, instance, organization);
         return recordStatus(resource, Status.ERROR,
               "Authentication to control plane failed: " + safeMessage(e)).rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /**
    * Perform the actual reconciliation logic with an authenticated API client.
    * Concrete reconcilers implement this method instead of {@link Reconciler#reconcile}.
    * @param resource  The custom resource to reconcile.
    * @param context   The reconciliation context.
    * @param apiClient An authenticated {@link ApiClient} targeting the correct
    *                  control plane instance and organization.
    * @return The update control result.
    */
   protected abstract UpdateControl<R> doReconcile(R resource, Context<R> context, ApiClient apiClient);

   /**
    * Record a reconciliation status (typically an error or progress condition) into the
    * resource's typed status. Invoked by {@link #reconcile} for precondition and
    * authentication failures so they become visible on the custom resource.
    * <p>
    * The default implementation performs no status update (returns {@link UpdateControl#noUpdate()});
    * reconcilers backed by a status carrying {@code status}/{@code message} fields should override it.
    * @param resource The custom resource being reconciled.
    * @param status   The status value to set (e.g. {@link Status#ERROR}).
    * @param message  A human-readable, sanitized message.
    * @return The update control to return from {@link #reconcile}.
    */
   protected UpdateControl<R> recordStatus(R resource, Status status, String message) {
      return UpdateControl.noUpdate();
   }

   /**
    * Produce a concise, single-line, length-bounded message suitable for storing in a CR status.
    * <p>
    * Exception messages may embed a raw HTTP response body, which can be large or contain
    * sensitive data; this collapses whitespace/newlines and truncates the result so the status
    * stays readable and safe to expose.
    * @param throwable The throwable whose message should be sanitized.
    * @return A single-line, length-bounded message, never {@code null}.
    */
   protected static String safeMessage(Throwable throwable) {
      String raw = throwable.getMessage();
      if (raw == null || raw.isBlank()) {
         return throwable.getClass().getSimpleName();
      }
      String normalized = raw.strip().replaceAll("\\s+", " ");
      if (normalized.length() > MAX_STATUS_MESSAGE_LENGTH) {
         return normalized.substring(0, MAX_STATUS_MESSAGE_LENGTH - 1) + "…";
      }
      return normalized;
   }

   /**
    * Build an authenticated {@link ApiClient} for the given resource by resolving
    * the {@code reshapr.io/instance} and {@code reshapr.io/organization} annotations.
    * Useful outside the main reconcile flow, e.g. during cleanup.
    * @param resource The custom resource carrying the reShapr annotations.
    * @return An authenticated {@link ApiClient}, or {@code null} if the required
    *         annotations are missing.
    * @throws ReshaprAuthenticationException if authentication against the control plane fails.
    */
   protected ApiClient authenticatedClientFor(R resource) throws ReshaprAuthenticationException {
      Map<String, String> annotations = resource.getMetadata().getAnnotations();
      if (annotations == null) {
         return null;
      }
      String instance = annotations.get(ReshaprAnnotations.INSTANCE);
      String organization = annotations.get(ReshaprAnnotations.ORGANIZATION);
      if (instance == null || instance.isBlank() || organization == null || organization.isBlank()) {
         return null;
      }
      return apiClientFactory.createAuthenticatedApiClient(instance, organization);
   }

   /**
    * Find a control plane Service matching the given name and, when provided, version.
    * Paginates through the control plane Services until a match is found or the list is exhausted.
    * @return the matching {@link io.reshapr.client.model.Service}, or {@code null} if none matches.
    */
   protected io.reshapr.client.model.Service findRemoteService(DefaultApi api, String name, String version)
         throws ApiException {
      BigDecimal size = BigDecimal.valueOf(50);
      int pageNumber = 0;
      while (true) {
         List<io.reshapr.client.model.Service> page = api.getServices(BigDecimal.valueOf(pageNumber), size);
         if (page == null || page.isEmpty()) {
            return null;
         }
         for (io.reshapr.client.model.Service candidate : page) {
            boolean nameMatches = name.equals(candidate.getName());
            boolean versionMatches = version == null || version.isBlank() || version.equals(candidate.getVersion());
            if (nameMatches && versionMatches) {
               return candidate;
            }
         }
         if (page.size() < 50) {
            return null;
         }
         pageNumber++;
      }
   }
}
