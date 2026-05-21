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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Service responsible for authenticating with the reShapr control plane
 * using the projected service account token pattern.
 * <p>
 * Calls {@code POST /auth/login/token/service-account} on the target control
 * plane instance, providing:
 * <ul>
 *   <li>{@code Authorization: Bearer <sa-token>} header</li>
 *   <li>{@code x-reshapr-organization: <organization>} header</li>
 * </ul>
 * The response body is expected to contain a JWT bearer token that can then be
 * used for subsequent API calls.
 * </p>
 * @author laurent
 */
@Singleton
public class ReshaprAuthenticationService {

   private static final Logger logger = Logger.getLogger(ReshaprAuthenticationService.class);

   private static final String AUTH_PATH = "/auth/login/token/service-account";
   private static final String ORGANIZATION_HEADER = "x-reshapr-organization";

   private final ServiceAccountTokenProvider tokenProvider;
   private final HttpClient httpClient;

   @Inject
   public ReshaprAuthenticationService(ServiceAccountTokenProvider tokenProvider) {
      this.tokenProvider = tokenProvider;
      this.httpClient = HttpClient.newHttpClient();
   }

   /**
    * Authenticate against the reShapr control plane and retrieve a JWT bearer token.
    * @param controlPlaneBaseUrl The base URL of the control plane instance
    *                            (e.g. {@code http://reshapr-cp.reshapr-system.svc.cluster.local:5555}).
    * @param organization        The organization name to impersonate.
    * @return A JWT bearer token to use for subsequent API calls.
    * @throws ReshaprAuthenticationException if authentication fails.
    */
   public String authenticate(String controlPlaneBaseUrl, String organization)
         throws ReshaprAuthenticationException {
      try {
         String saToken = tokenProvider.getToken();

         HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(controlPlaneBaseUrl + AUTH_PATH))
               .header("Authorization", "Bearer " + saToken)
               .header(ORGANIZATION_HEADER, organization)
               .POST(HttpRequest.BodyPublishers.noBody())
               .build();

         logger.infof("Authenticating to reShapr control plane at '%s' for organization '%s'",
               controlPlaneBaseUrl, organization);

         HttpResponse<String> response = httpClient.send(request,
               HttpResponse.BodyHandlers.ofString());

         if (response.statusCode() >= 200 && response.statusCode() < 300) {
            logger.debugf("Successfully authenticated for organization '%s'", organization);
            return response.body().trim();
         } else {
            throw new ReshaprAuthenticationException(
                  "Authentication failed with status " + response.statusCode()
                        + ": " + response.body());
         }
      } catch (IOException | InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ReshaprAuthenticationException("Failed to authenticate with reShapr control plane", e);
      }
   }
}

