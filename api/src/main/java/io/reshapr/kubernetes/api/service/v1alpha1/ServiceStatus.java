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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * This the {@code status} of a {@link Service} custom resource.
 * @author laurent
 */
public class ServiceStatus {

   @JsonPropertyDescription("Reconciled generation")
   private long observedGeneration;

   public long getObservedGeneration() {
      return observedGeneration;
   }

   public void setObservedGeneration(long observedGeneration) {
      this.observedGeneration = observedGeneration;
   }
}
