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
 * The specification of a single Secret entry within a {@link SecretSourceSpec}. Values
 * may either be inlined or loaded at reconciliation time from a Kubernetes {@code Secret}
 * via {@link SecretValuesFromSpec}.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "description", "type", "username", "password", "token", "tokenHeader", "certPem", "useElicitation", "oauth2ClientConfiguration", "valuesFrom" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class SecretSpec {

   @JsonPropertyDescription("The name of the Secret to create in the reShapr target instance")
   private String name;

   @JsonPropertyDescription("The description of the Secret to create in the reShapr target instance")
   private String description;

   @JsonPropertyDescription("The type of the Secret — one of ARTIFACT or ENDPOINT. Defaults to ENDPOINT")
   private String type = "ENDPOINT";

   @JsonPropertyDescription("A username for a Secret holding basic authentication information")
   private String username;

   @JsonPropertyDescription("A password for a Secret holding basic authentication information. Must be provided with username")
   private String password;

   @JsonPropertyDescription("A token for a Secret holding token-based authentication information")
   private String token;

   @JsonPropertyDescription("Header used to transport the token of a Secret holding token-based authentication information")
   private String tokenHeader;

   @JsonPropertyDescription("A certificate or certificate chain in PEM format for a Secret holding TLS authentication information")
   private String certPem;

   @JsonPropertyDescription("Whether the Secret should be resolved through elicitation at runtime instead of being stored in the control plane")
   private Boolean useElicitation;

   @JsonPropertyDescription("OAuth2 client configuration carried by this Secret")
   private OAuth2ClientConfigurationSpec oauth2ClientConfiguration;

   @JsonPropertyDescription("Optional reference to a Kubernetes Secret from which the values must be loaded at reconcile time")
   private SecretValuesFromSpec valuesFrom;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   public String getTokenHeader() {
      return tokenHeader;
   }

   public void setTokenHeader(String tokenHeader) {
      this.tokenHeader = tokenHeader;
   }

   public String getCertPem() {
      return certPem;
   }

   public void setCertPem(String certPem) {
      this.certPem = certPem;
   }

   public Boolean getUseElicitation() {
      return useElicitation;
   }

   public void setUseElicitation(Boolean useElicitation) {
      this.useElicitation = useElicitation;
   }

   public OAuth2ClientConfigurationSpec getOauth2ClientConfiguration() {
      return oauth2ClientConfiguration;
   }

   public void setOauth2ClientConfiguration(OAuth2ClientConfigurationSpec oauth2ClientConfiguration) {
      this.oauth2ClientConfiguration = oauth2ClientConfiguration;
   }

   public SecretValuesFromSpec getValuesFrom() {
      return valuesFrom;
   }

   public void setValuesFrom(SecretValuesFromSpec valuesFrom) {
      this.valuesFrom = valuesFrom;
   }
}
