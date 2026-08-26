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
package io.reshapr.kubernetes.api.exposition.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.sundr.builder.annotations.Buildable;

/**
 * This the {@code specification} of an {@link Exposition} custom resource.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "service", "configurationPlan", "gatewayGroup", "keepOnDelete" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ExpositionSpec {

   @JsonPropertyDescription("Reference to the target Service (by name and optional version) — used to scope the ConfigurationPlan lookup, since plan names are not globally unique")
   private ServiceRef service;

   @JsonPropertyDescription("Name of the target ConfigurationPlan attached to the Service")
   private String configurationPlan;

   @JsonPropertyDescription("Name of the target GatewayGroup on which the Service must be exposed")
   private String gatewayGroup;

   @JsonPropertyDescription("Flag to keep Exposition when deleting this resource. Default is false")
   private boolean keepOnDelete = false;

   public ServiceRef getService() {
      return service;
   }

   public void setService(ServiceRef service) {
      this.service = service;
   }

   public String getConfigurationPlan() {
      return configurationPlan;
   }

   public void setConfigurationPlan(String configurationPlan) {
      this.configurationPlan = configurationPlan;
   }

   public String getGatewayGroup() {
      return gatewayGroup;
   }

   public void setGatewayGroup(String gatewayGroup) {
      this.gatewayGroup = gatewayGroup;
   }

   public boolean isKeepOnDelete() {
      return keepOnDelete;
   }

   public void setKeepOnDelete(boolean keepOnDelete) {
      this.keepOnDelete = keepOnDelete;
   }
}
