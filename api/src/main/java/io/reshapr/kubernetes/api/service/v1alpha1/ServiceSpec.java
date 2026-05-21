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
package io.reshapr.kubernetes.api.service.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

import java.util.List;

/**
 * This the {@code specification} of a {@link Service} custom resource.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "version", "url", "secretRef", "keepOnDelete" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ServiceSpec {

   @JsonPropertyDescription("The name of the service if you need to override the one from the artifact")
   private String name;

   @JsonPropertyDescription("The version of the service if you need to override the one from the artifact")
   private String version;

   @JsonPropertyDescription("The URL to access this service remote artifact definition")
   private String url;

   @JsonPropertyDescription("The list of included operations")
   private List<String> includedOperations;

   @JsonPropertyDescription("The list of excluded operations")
   private List<String> excludedOperations;

   @JsonPropertyDescription("Reference to a Secret for accessing the artifact url")
   private String secretRef;

   @JsonPropertyDescription("Flag to keep Service when deleting this resource. Default is false")
   private boolean keepOnDelete = false;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getVersion() {
      return version;
   }

   public void setVersion(String version) {
      this.version = version;
   }

   public String getUrl() {
      return url;
   }

   public void setUrl(String url) {
      this.url = url;
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

   public String getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(String secretRef) {
      this.secretRef = secretRef;
   }

   public boolean isKeepOnDelete() {
      return keepOnDelete;
   }

   public void setKeepOnDelete(boolean keepOnDelete) {
      this.keepOnDelete = keepOnDelete;
   }
}
