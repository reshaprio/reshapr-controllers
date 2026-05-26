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
import io.fabric8.kubernetes.client.KubernetesClient;
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

   public static AdmissionController<Pod> mutatingController(KubernetesClient client) {
      return new AdmissionController<>(new PodMutator(client));
   }

   /**
    * Mutates Pods to inject the Reshapr Proxy container if annotated.
    */
   public static class PodMutator implements Mutator<Pod> {

      private final KubernetesClient client;

      public PodMutator(KubernetesClient client) {
          this.client = client;
      }

      @Override
      public Pod mutate(Pod resource, Operation operation) throws NotAllowedException {
         Map<String, String> annotations = resource.getMetadata().getAnnotations();
         if (annotations != null && "true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION))) {
            
            // Check if already injected
            if (resource.getSpec().getContainers() != null && resource.getSpec().getContainers().stream().anyMatch(c -> PROXY_CONTAINER_NAME.equals(c.getName()))) {
               return resource;
            }

            // Fetch defaults from ConfigMap
            String namespace = resource.getMetadata().getNamespace();
            if (namespace == null) {
                // If namespace is not set on the resource, it defaults to "default" in Kubernetes
                namespace = "default";
            }
            
            Map<String, String> configMapData = new HashMap<>();
            try {
                var configMap = client.configMaps().inNamespace(namespace).withName("reshapr-injection-config").get();
                if (configMap != null && configMap.getData() != null) {
                    configMapData = configMap.getData();
                }
            } catch (Exception e) {
                // Ignore if ConfigMap doesn't exist or client fails
            }

            ContainerBuilder proxyBuilder = new ContainerBuilder()
                  .withName(PROXY_CONTAINER_NAME)
                  .withImage(DEFAULT_PROXY_IMAGE)
                  .addNewPort()
                     .withContainerPort(8080)
                     .withName("proxy")
                  .endPort();
                  
            // 1. Inject control plane URL
            String controlPlaneUrl = annotations.containsKey(CONTROL_PLANE_URL_ANNOTATION) 
                ? annotations.get(CONTROL_PLANE_URL_ANNOTATION) 
                : configMapData.get("control-plane-url");
                
            if (controlPlaneUrl != null && !controlPlaneUrl.isBlank()) {
               proxyBuilder.addNewEnv()
                     .withName("RESHAPR_CONTROL_PLANE_URL")
                     .withValue(controlPlaneUrl)
                     .endEnv();
            }

            // 2. Inject secret as envFrom
            String secretName = annotations.containsKey(TOKEN_SECRET_NAME_ANNOTATION) 
                ? annotations.get(TOKEN_SECRET_NAME_ANNOTATION) 
                : configMapData.get("token-secret-name");
                
            if (secretName != null && !secretName.isBlank()) {
               proxyBuilder.addNewEnvFrom()
                     .withNewSecretRef()
                        .withName(secretName)
                     .endSecretRef()
                     .endEnvFrom();
            }
            
            // 3. Inject Gateway Labels
            String gatewayLabels = annotations.containsKey("io.reshapr/gateway-labels") 
                ? annotations.get("io.reshapr/gateway-labels") 
                : configMapData.get("gateway-labels");
                
            if (gatewayLabels != null && !gatewayLabels.isBlank()) {
               proxyBuilder.addNewEnv()
                     .withName("RESHAPR_GATEWAY_LABELS")
                     .withValue(gatewayLabels)
                     .endEnv();
            }

            // 4. Inject Gateway Instance (Control Plane Binding)
            String gatewayInstance = annotations.containsKey("io.reshapr/instance") 
                ? annotations.get("io.reshapr/instance") 
                : configMapData.get("instance");
                
            if (gatewayInstance != null && !gatewayInstance.isBlank()) {
               proxyBuilder.addNewEnv()
                     .withName("RESHAPR_GATEWAY_INSTANCE")
                     .withValue(gatewayInstance)
                     .endEnv();
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
