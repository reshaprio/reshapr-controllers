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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.reshapr.kubernetes.api.model.Status;

/**
 * This the {@code status} of an {@link Exposition} custom resource.
 * @author laurent
 */
public class ExpositionStatus {

   @JsonPropertyDescription("Global status of the reconciliation")
   private Status status = Status.UNKNOWN;

   @JsonPropertyDescription("Detailed message about the current status")
   private String message;

   @JsonPropertyDescription("Reconciled generation")
   private long observedGeneration;

   @JsonPropertyDescription("Identifier of the corresponding Exposition in the reShapr control plane")
   private String expositionId;

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

   public long getObservedGeneration() {
      return observedGeneration;
   }

   public void setObservedGeneration(long observedGeneration) {
      this.observedGeneration = observedGeneration;
   }

   public String getExpositionId() {
      return expositionId;
   }

   public void setExpositionId(String expositionId) {
      this.expositionId = expositionId;
   }
}
