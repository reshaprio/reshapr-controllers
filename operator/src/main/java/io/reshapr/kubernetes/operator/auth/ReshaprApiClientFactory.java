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
package io.reshapr.kubernetes.operator.auth;

import io.reshapr.client.ApiClient;
import io.reshapr.client.api.DefaultApi;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Factory for creating authenticated reShapr API clients.
 * Resolves the control plane base URL from the Kubernetes Service name found in
 * the {@code reshapr.io/instance} annotation, authenticates via projected SA token,
 * and returns a ready-to-use {@link DefaultApi} client with the JWT bearer set.
 * @author laurent
 */
@Singleton
public class ReshaprApiClientFactory {

   private static final Logger logger = Logger.getLogger(ReshaprApiClientFactory.class);

   /** Default port for the reShapr control plane service. */
   private static final int DEFAULT_CP_PORT = 5555;

   /** Default namespace where the control plane runs. */
   private static final String DEFAULT_CP_NAMESPACE = "reshapr-system";

   private final ReshaprAuthenticationService authService;

   @Inject
   public ReshaprApiClientFactory(ReshaprAuthenticationService authService) {
      this.authService = authService;
   }

   /**
    * Build the control plane base URL from a Kubernetes Service name.
    * Resolves using in-cluster DNS: {@code http://<serviceName>.<namespace>.svc.cluster.local:<port>}
    * @param serviceName The Kubernetes Service name (from {@code reshapr.io/instance} annotation).
    * @return The base URL of the control plane.
    */
   public String resolveControlPlaneUrl(String serviceName) {
      return resolveControlPlaneUrl(serviceName, DEFAULT_CP_NAMESPACE, DEFAULT_CP_PORT);
   }

   /**
    * Build the control plane base URL from a Kubernetes Service name, namespace and port.
    * @param serviceName The Kubernetes Service name.
    * @param namespace   The namespace of the control plane service.
    * @param port        The port of the control plane service.
    * @return The base URL of the control plane.
    */
   public String resolveControlPlaneUrl(String serviceName, String namespace, int port) {
      // If service name already ends with the cluster local suffix, use it as is.
      if (serviceName.endsWith(".svc.cluster.local")) {
         return "http://" + serviceName + ":" + port;
      }
      // If service name contains a dot, assume it's qualified with namespace.
      if (serviceName.contains(".")) {
         return "http://" + serviceName + ".svc.cluster.local:" + port;
      }
      // Else append default namespace.
      return "http://" + serviceName + "." + namespace + ".svc.cluster.local:" + port;
   }

   /**
    * Create an authenticated {@link DefaultApi} client for the given control plane
    * instance and organization.
    * @param instanceServiceName The Kubernetes Service name of the control plane
    *                            (from the {@code reshapr.io/instance} annotation).
    * @param organization        The organization to impersonate
    *                            (from the {@code reshapr.io/organization} annotation).
    * @return An authenticated {@link DefaultApi} ready to use.
    * @throws ReshaprAuthenticationException if authentication fails.
    */
   public DefaultApi createAuthenticatedApi(String instanceServiceName, String organization)
         throws ReshaprAuthenticationException {
      return new DefaultApi(createAuthenticatedApiClient(instanceServiceName, organization));
   }

   /**
    * Create an authenticated low-level {@link ApiClient} for the given control plane
    * instance and organization. Useful to share the same authenticated client between
    * the generated {@link DefaultApi} and hand-written calls (e.g. form-urlencoded endpoints).
    * @param instanceServiceName The Kubernetes Service name of the control plane
    *                            (from the {@code reshapr.io/instance} annotation).
    * @param organization        The organization to impersonate
    *                            (from the {@code reshapr.io/organization} annotation).
    * @return An authenticated {@link ApiClient} with base URI and JWT bearer configured.
    * @throws ReshaprAuthenticationException if authentication fails.
    */
   public ApiClient createAuthenticatedApiClient(String instanceServiceName, String organization)
         throws ReshaprAuthenticationException {

      String baseUrl = resolveControlPlaneUrl(instanceServiceName);
      logger.infof("Creating authenticated API client for instance=%s, organization=%s",
            instanceServiceName, organization);

      // Authenticate and obtain a JWT bearer token.
      String jwtToken = authService.authenticate(baseUrl, organization);

      // Configure the generated API client with the base URL and JWT bearer.
      ApiClient apiClient = new ApiClient();

      // Now we must also add the '/api' prefix to access non-auth API endpoints if not already provided.
      if (!baseUrl.endsWith("/api")) {
         baseUrl += "/api";
      }
      apiClient.updateBaseUri(baseUrl);
      apiClient.setRequestInterceptor(builder ->
            builder.header("Authorization", "Bearer " + jwtToken)
      );

      return apiClient;
   }
}
