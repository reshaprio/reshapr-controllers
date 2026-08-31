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
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.mutation.Mutator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

   public static class PodMutator implements Mutator<Pod> {

      @Override
      public Pod mutate(Pod resource, Operation operation) throws NotAllowedException {
         Map<String, String> annotations = resource.getMetadata().getAnnotations();

         if (annotations != null && "true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION))) {

            // Check if already injected
            if (resource.getSpec().getContainers() != null &&
                  resource.getSpec().getContainers().stream()
                        .anyMatch(c -> PROXY_CONTAINER_NAME.equals(c.getName()))) {
               return resource;
            }

            String namespace = resource.getMetadata().getNamespace();
            if (namespace == null) {
                namespace = "reshapr-system";
            }

            // Determine service name based on owner reference (ReplicaSet -> Deployment)
            String deploymentName = "unknown";
            if (resource.getMetadata().getOwnerReferences() != null && !resource.getMetadata().getOwnerReferences().isEmpty()) {
                OwnerReference owner = resource.getMetadata().getOwnerReferences().get(0);
                if ("ReplicaSet".equals(owner.getKind())) {
                    String rsName = owner.getName();
                    int lastDash = rsName.lastIndexOf('-');
                    if (lastDash > 0) {
                        deploymentName = rsName.substring(0, lastDash);
                    } else {
                        deploymentName = rsName;
                    }
                } else {
                    deploymentName = owner.getName();
                }
            } else if (resource.getMetadata().getLabels() != null && resource.getMetadata().getLabels().containsKey("app")) {
                deploymentName = resource.getMetadata().getLabels().get("app");
            }
            String serviceName = "reshapr-proxy-" + deploymentName;
            String dnsQuery = serviceName + "." + namespace + ".svc.cluster.local";

            List<EnvVar> envVars = new ArrayList<>();

            // 1. Inject control plane URL
            String controlPlaneUrl = annotations.get(CONTROL_PLANE_URL_ANNOTATION);
            if (controlPlaneUrl != null && !controlPlaneUrl.isBlank()) {
               envVars.add(new EnvVarBuilder()
                     .withName("RESHAPR_CONTROL_PLANE_URL")
                     .withValue(controlPlaneUrl)
                     .build());
            }

            // 2. POD_IP for JGroups bind address
            envVars.add(new EnvVarBuilder()
                    .withName("POD_IP")
                    .withValueFrom(new EnvVarSourceBuilder()
                            .withNewFieldRef()
                                .withFieldPath("status.podIP")
                            .endFieldRef()
                            .build())
                    .build());

            // 3. JAVA_OPTS_APPEND for JGroups & Infinispan
            String javaOpts = "-XX:+UseCompactObjectHeaders " +
                    "-Dquarkus.http.host=0.0.0.0 " +
                    "-Djava.util.logging.manager=org.jboss.logmanager.LogManager " +
                    "--enable-preview " +
                    "-Dreshapr.infinispan.stack=reshapr-k8s " +
                    "-Dreshapr.infinispan.dns-query=" + dnsQuery + " " +
                    "-Djgroups.port_range=0 " +
                    "-Djgroups.bind.address=$(POD_IP)";
            
            envVars.add(new EnvVarBuilder()
                    .withName("JAVA_OPTS_APPEND")
                    .withValue(javaOpts)
                    .build());

            ContainerBuilder proxyBuilder = new ContainerBuilder()
                  .withName(PROXY_CONTAINER_NAME)
                  .withImage(DEFAULT_PROXY_IMAGE)
                  .withEnv(envVars)
                  .addNewPort()
                     .withContainerPort(8080)
                     .withName("proxy")
                  .endPort()
                  .addNewPort()
                     .withContainerPort(7778)
                     .withName("jgroups")
                  .endPort()
                  .addNewPort()
                     .withContainerPort(57778)
                     .withName("jgroups-fd")
                  .endPort();

            // Inject secret as envFrom if specified
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
