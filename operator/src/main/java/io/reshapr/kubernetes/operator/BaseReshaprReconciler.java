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

import io.reshapr.client.api.DefaultApi;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.jboss.logging.Logger;

import java.util.Map;
/**
 * Abstract base reconciler providing authentication and API client setup for
 * all reShapr custom resource reconcilers.
 * Each reconciliation cycle extracts the {@code reshapr.io/instance} and
 * {@code reshapr.io/organization} annotations from the custom resource,
 * authenticates with the control plane using the projected service account
 * token, and delegates to the concrete {@link #doReconcile} method with an
 * authenticated {@link DefaultApi} client.
 *
 * @param <R> The custom resource type being reconciled.
 * @author laurent
 */
public abstract class BaseReshaprReconciler<R extends CustomResource<?, ?>> implements Reconciler<R> {

   private static final Logger logger = Logger.getLogger(BaseReshaprReconciler.class);

   private ReshaprApiClientFactory apiClientFactory;

   /** No-args constructor required by CDI for proxying. */
   BaseReshaprReconciler() {
   }

   protected BaseReshaprReconciler(ReshaprApiClientFactory apiClientFactory) {
      this.apiClientFactory = apiClientFactory;
   }

   @Override
   public final UpdateControl<R> reconcile(R resource, Context<R> context) {
      Map<String, String> annotations = resource.getMetadata().getAnnotations();

      if (annotations == null) {
         logger.warnf("Resource %s/%s has no annotations — skipping reconciliation",
               resource.getMetadata().getNamespace(), resource.getMetadata().getName());
         return UpdateControl.noUpdate();
      }

      String instance = annotations.get(ReshaprAnnotations.INSTANCE);
      String organization = annotations.get(ReshaprAnnotations.ORGANIZATION);

      if (instance == null || instance.isBlank()) {
         logger.warnf("Resource %s/%s is missing annotation %s — skipping reconciliation",
               resource.getMetadata().getNamespace(), resource.getMetadata().getName(),
               ReshaprAnnotations.INSTANCE);
         return UpdateControl.noUpdate();
      }
      if (organization == null || organization.isBlank()) {
         logger.warnv("Resource %s/%s is missing annotation %s — skipping reconciliation",
               resource.getMetadata().getNamespace(), resource.getMetadata().getName(),
               ReshaprAnnotations.ORGANIZATION);
         return UpdateControl.noUpdate();
      }

      try {
         DefaultApi api = apiClientFactory.createAuthenticatedApi(instance, organization);
         return doReconcile(resource, context, api);
      } catch (ReshaprAuthenticationException e) {
         logger.errorv(e, "Authentication failed for resource %s/%s targeting instance=%s, organization=%s",
               resource.getMetadata().getNamespace(), resource.getMetadata().getName(),
               instance, organization);
         // Re-schedule reconciliation by returning noUpdate — the operator SDK will retry.
         return UpdateControl.noUpdate();
      }
   }

   /**
    * Perform the actual reconciliation logic with an authenticated API client.
    * Concrete reconcilers implement this method instead of {@link Reconciler#reconcile(Object, Context)}.
    * @param resource The custom resource to reconcile.
    * @param context  The reconciliation context.
    * @param api      An authenticated {@link DefaultApi} client targeting the
    *                 correct control plane instance and organization.
    * @return The update control result.
    */
   protected abstract UpdateControl<R> doReconcile(R resource, Context<R> context, DefaultApi api);
}

