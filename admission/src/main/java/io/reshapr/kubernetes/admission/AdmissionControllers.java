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
package io.reshapr.kubernetes.admission;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.mutation.Mutator;

import java.util.HashMap;

/**
 * @author laurent
 */
public class AdmissionControllers {

   private AdmissionControllers() {
      // Private constructor to prevent instantiation.
   }

   public static AdmissionController<Pod> mutatingController() {
      return new AdmissionController<>(new PodMutator());
   }

   /**
    *
    */
   public static class PodMutator implements Mutator<Pod> {

      @Override
      public Pod mutate(Pod resource, Operation operation) throws NotAllowedException {
         // Example mutation: add a label to the Pod
         if (resource.getMetadata().getLabels() == null) {
            resource.getMetadata().setLabels(new HashMap<>());
         }
         resource.getMetadata().getLabels().put("mutated", "true");
         return resource;
      }
   }
}
