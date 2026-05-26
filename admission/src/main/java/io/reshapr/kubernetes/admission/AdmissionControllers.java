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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.mutation.Mutator;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * @author laurent
 */
public class AdmissionControllers {

   public static final String INJECT_ANNOTATION = "io.reshapr/inject";
   public static final String CONTROL_PLANE_URL_ANNOTATION = "io.reshapr/control-plane-url";
   public static final String TOKEN_SECRET_NAME_ANNOTATION = "io.reshapr/token-secret-name";
   
   public static final String PROXY_INJECTED_LABEL = "reshapr.io/proxy-injected";
   
   public static final String PROXY_CONTAINER_NAME = "reshapr-proxy";
   public static final String DEFAULT_PROXY_IMAGE = "quay.io/reshapr/reshapr-proxy:latest";

   private AdmissionControllers() {
      // Private constructor to prevent instantiation.
   }

   public static AdmissionController<Pod> mutatingController() {
      return new AdmissionController<>(new PodMutator());
   }

   /**
    * Mutates Pods to inject the Reshapr Proxy container if annotated.
    */
   public static class PodMutator implements Mutator<Pod> {

      @Override
      public Pod mutate(Pod resource, Operation operation) throws NotAllowedException {
         Map<String, String> annotations = resource.getMetadata().getAnnotations();
         if (annotations != null && "true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION))) {
            
            // Check if already injected
            if (resource.getSpec().getContainers() != null && resource.getSpec().getContainers().stream().anyMatch(c -> PROXY_CONTAINER_NAME.equals(c.getName()))) {
               return resource;
            }

            ContainerBuilder proxyBuilder = new ContainerBuilder()
                  .withName(PROXY_CONTAINER_NAME)
                  .withImage(DEFAULT_PROXY_IMAGE);
                  
            // Inject control plane URL env
            String controlPlaneUrl = annotations.get(CONTROL_PLANE_URL_ANNOTATION);
            if (controlPlaneUrl != null && !controlPlaneUrl.isBlank()) {
               proxyBuilder.addNewEnv()
                     .withName("RESHAPR_CONTROL_PLANE_URL")
                     .withValue(controlPlaneUrl)
                     .endEnv();
            }

            // Inject secret as envFrom
            String secretName = annotations.get(TOKEN_SECRET_NAME_ANNOTATION);
            if (secretName != null && !secretName.isBlank()) {
               proxyBuilder.addNewEnvFrom()
                     .withNewSecretRef()
                        .withName(secretName)
                     .endSecretRef()
                     .endEnvFrom();
            }

            // Add the container to the Pod
            if (resource.getSpec().getContainers() == null) {
                resource.getSpec().setContainers(new ArrayList<>());
            }
            resource.getSpec().getContainers().add(proxyBuilder.build());

            // Add the routing label
            if (resource.getMetadata().getLabels() == null) {
               resource.getMetadata().setLabels(new HashMap<>());
            }
            resource.getMetadata().getLabels().put(PROXY_INJECTED_LABEL, "true");
         }
         return resource;
      }
   }
}
