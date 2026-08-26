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
package io.reshapr.kubernetes.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;

/**
 * Represents a per-item reconciliation condition, typically used to track the status
 * of individual sub-resources managed by an aggregating custom resource (e.g. one entry
 * per secret in a {@code SecretSource}).
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class Condition {

   @JsonPropertyDescription("Type of the condition — typically the sub-resource name it refers to")
   private String type;

   @JsonPropertyDescription("Status of the condition")
   private Status status = Status.UNKNOWN;

   @JsonPropertyDescription("Human-readable message about the condition (often the control plane resource id)")
   private String message;

   @JsonPropertyDescription("ISO-8601 timestamp of the last transition of this condition")
   private String lastTransitionTime;

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public Status getStatus() {
      return status;
   }

   public void setStatus(Status status) {
      this.status = status;
   }

   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public String getLastTransitionTime() {
      return lastTransitionTime;
   }

   public void setLastTransitionTime(String lastTransitionTime) {
      this.lastTransitionTime = lastTransitionTime;
   }
}
