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
package io.reshapr.kubernetes.api.configurationplan.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;
import io.reshapr.kubernetes.api.model.ServiceRef;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "service", "backendEndpoint", "apiKey", "oauth2", "artifacts", "audit", "includedOperations", "excludedOperations", "cachePolicy" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ConfigurationPlanSpec {

   @JsonPropertyDescription("Reference to the target Service")
   private ServiceRef service;

   @JsonPropertyDescription("Backend endpoint for this Configuration Plan")
   private String backendEndpoint;

   @JsonPropertyDescription("Whether an API Key should be generated")
   private boolean apiKey = false;

   @JsonPropertyDescription("OAuth2 Client Configuration")
   private OAuth2Spec oauth2;

   @JsonPropertyDescription("Reserved for future usage")
   private List<String> artifacts;

   @JsonPropertyDescription("Whether the audit log will be enabled and calls logged. Default is false.")
   private Boolean audit = false;

   @JsonPropertyDescription("List of included operation names")
   private List<String> includedOperations;

   @JsonPropertyDescription("List of excluded operation names")
   private List<String> excludedOperations;

   @JsonPropertyDescription("The cache policy to apply")
   private String cachePolicy;

   public ServiceRef getService() {
      return service;
   }

   public void setService(ServiceRef service) {
      this.service = service;
   }

   public String getBackendEndpoint() {
      return backendEndpoint;
   }

   public void setBackendEndpoint(String backendEndpoint) {
      this.backendEndpoint = backendEndpoint;
   }

   public boolean isApiKey() {
      return apiKey;
   }

   public void setApiKey(boolean apiKey) {
      this.apiKey = apiKey;
   }

   public OAuth2Spec getOauth2() {
      return oauth2;
   }

   public void setOauth2(OAuth2Spec oauth2) {
      this.oauth2 = oauth2;
   }

   public List<String> getArtifacts() {
      return artifacts;
   }

   public void setArtifacts(List<String> artifacts) {
      this.artifacts = artifacts;
   }

   public Boolean getAudit() {
      return audit;
   }

   public void setAudit(Boolean audit) {
      this.audit = audit;
   }

   public List<String> getIncludedOperations() {
      return includedOperations;
   }

   public void setIncludedOperations(List<String> includedOperations) {
      this.includedOperations = includedOperations;
   }

   public List<String> getExcludedOperations() {
      return excludedOperations;
   }

   public void setExcludedOperations(List<String> excludedOperations) {
      this.excludedOperations = excludedOperations;
   }

   public String getCachePolicy() {
      return cachePolicy;
   }

   public void setCachePolicy(String cachePolicy) {
      this.cachePolicy = cachePolicy;
   }
}
