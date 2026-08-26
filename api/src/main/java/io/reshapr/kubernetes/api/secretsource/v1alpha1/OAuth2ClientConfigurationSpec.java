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
package io.reshapr.kubernetes.api.secretsource.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

/**
 * OAuth2 client configuration carried by a {@link SecretSpec}. Mirrors the
 * {@code OAuth2ClientConfiguration} schema of the reShapr control plane.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "clientId", "clientSecret", "authorizationEndpoint", "tokenEndpoint" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class OAuth2ClientConfigurationSpec {

   @JsonPropertyDescription("OAuth2 Client ID")
   private String clientId;

   @JsonPropertyDescription("OAuth2 Client Secret")
   private String clientSecret;

   @JsonPropertyDescription("Authorization endpoint of the OAuth2 authorization server")
   private String authorizationEndpoint;

   @JsonPropertyDescription("Token exchange endpoint of the OAuth2 authorization server")
   private String tokenEndpoint;

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getAuthorizationEndpoint() {
      return authorizationEndpoint;
   }

   public void setAuthorizationEndpoint(String authorizationEndpoint) {
      this.authorizationEndpoint = authorizationEndpoint;
   }

   public String getTokenEndpoint() {
      return tokenEndpoint;
   }

   public void setTokenEndpoint(String tokenEndpoint) {
      this.tokenEndpoint = tokenEndpoint;
   }
}
