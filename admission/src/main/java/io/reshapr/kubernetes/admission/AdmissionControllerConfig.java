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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * @author laurent
 */
public class AdmissionControllerConfig {

   public static final String MUTATING_CONTROLLER = "mutatingController";

   @Inject
   KubernetesClient client;

   @Singleton
   @Named(MUTATING_CONTROLLER)
   public AdmissionController<Pod> mutatingController() {
      return AdmissionControllers.mutatingController(client);
   }
}
