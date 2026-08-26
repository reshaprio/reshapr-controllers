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
 * Reference to a Kubernetes {@code Secret} whose entries are used to populate a
 * {@link SecretSpec} at reconcile time.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "secretRef", "usernameKey", "passwordKey", "tokenKey", "tokenHeaderKey", "certPemKey", "oauth2ClientSecretKey" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class SecretValuesFromSpec {

   @JsonPropertyDescription("The name of the source Kubernetes Secret to synchronize into reShapr")
   private String secretRef;

   @JsonPropertyDescription("The Secret key containing a username for a Secret holding basic authentication information")
   private String usernameKey;

   @JsonPropertyDescription("The Secret key containing a password for a Secret holding basic authentication information")
   private String passwordKey;

   @JsonPropertyDescription("The Secret key containing a token for a Secret holding token-based authentication information")
   private String tokenKey;

   @JsonPropertyDescription("The Secret key containing the header used to transport a token of a Secret holding token-based authentication information")
   private String tokenHeaderKey;

   @JsonPropertyDescription("The Secret key containing a certificate or certificate chain in PEM format for a Secret holding TLS authentication information")
   private String certPemKey;

   @JsonPropertyDescription("The Secret key containing the OAuth2 client secret to inject into the OAuth2 client configuration")
   private String oauth2ClientSecretKey;

   public String getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(String secretRef) {
      this.secretRef = secretRef;
   }

   public String getUsernameKey() {
      return usernameKey;
   }

   public void setUsernameKey(String usernameKey) {
      this.usernameKey = usernameKey;
   }

   public String getPasswordKey() {
      return passwordKey;
   }

   public void setPasswordKey(String passwordKey) {
      this.passwordKey = passwordKey;
   }

   public String getTokenKey() {
      return tokenKey;
   }

   public void setTokenKey(String tokenKey) {
      this.tokenKey = tokenKey;
   }

   public String getTokenHeaderKey() {
      return tokenHeaderKey;
   }

   public void setTokenHeaderKey(String tokenHeaderKey) {
      this.tokenHeaderKey = tokenHeaderKey;
   }

   public String getCertPemKey() {
      return certPemKey;
   }

   public void setCertPemKey(String certPemKey) {
      this.certPemKey = certPemKey;
   }

   public String getOauth2ClientSecretKey() {
      return oauth2ClientSecretKey;
   }

   public void setOauth2ClientSecretKey(String oauth2ClientSecretKey) {
      this.oauth2ClientSecretKey = oauth2ClientSecretKey;
   }
}
