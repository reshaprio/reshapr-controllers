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
package io.reshapr.kubernetes.operator;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@ControllerConfiguration
public class DeploymentProxyReconciler implements Reconciler<Deployment> {

    private static final Logger logger = Logger.getLogger(DeploymentProxyReconciler.class);
    
    public static final String INJECT_ANNOTATION = "io.reshapr/inject";
    public static final String PROXY_INJECTED_LABEL = "reshapr.io/proxy-injected";
    public static final int DEFAULT_PROXY_PORT = 7777;

    private final KubernetesClient client;

    public DeploymentProxyReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<Deployment> reconcile(Deployment deployment, Context<Deployment> context) {
        Map<String, String> annotations = deployment.getMetadata().getAnnotations();
        String namespace = deployment.getMetadata().getNamespace();
        if (namespace == null) {
            namespace = "reshapr-system";
        }
        
        String deploymentName = deployment.getMetadata().getName();
        String serviceName = "reshapr-proxy-" + deploymentName;

        if (annotations == null || !"true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION))) {
            Service existingService = client.services().inNamespace(namespace).withName(serviceName).get();
            if (existingService != null) {
                logger.infof("Injection annotation absent/removed. Deleting dedicated Service %s in namespace %s", serviceName, namespace);
                client.services().inNamespace(namespace).withName(serviceName).delete();
            }
            return UpdateControl.noUpdate();
        }

        logger.infof("Reconciling Deployment %s/%s for Reshapr Proxy Service", namespace, deploymentName);

        // Check if service already exists
        Service existingService = client.services().inNamespace(namespace).withName(serviceName).get();
        if (existingService == null) {
            logger.infof("Creating dedicated Service %s in namespace %s", serviceName, namespace);
            
            // Resolve proxy port
            int proxyPort = DEFAULT_PROXY_PORT;
            String portStr = annotations.get("io.reshapr/proxy-port");
            if (portStr == null) {
                try {
                    var configMap = client.configMaps().inNamespace(namespace).withName("reshapr-injection-config").get();
                    if (configMap != null && configMap.getData() != null && configMap.getData().containsKey("proxy-port")) {
                        portStr = configMap.getData().get("proxy-port");
                    }
                } catch (Exception ignored) {}
            }
            if (portStr != null && !portStr.isBlank()) {
                try {
                    proxyPort = Integer.parseInt(portStr);
                } catch (NumberFormatException ignored) {}
            }
            
            Map<String, String> serviceSelector = new HashMap<>();
            serviceSelector.put(PROXY_INJECTED_LABEL, "true");
            if (deployment.getSpec() != null && deployment.getSpec().getSelector() != null && deployment.getSpec().getSelector().getMatchLabels() != null) {
                serviceSelector.putAll(deployment.getSpec().getSelector().getMatchLabels());
            }

            Service newService = new ServiceBuilder()
                    .withNewMetadata()
                        .withName(serviceName)
                        .withNamespace(namespace)
                        // Add owner reference so it's deleted when deployment is deleted
                        .addNewOwnerReference()
                            .withApiVersion("apps/v1")
                            .withKind("Deployment")
                            .withName(deploymentName)
                            .withUid(deployment.getMetadata().getUid())
                        .endOwnerReference()
                    .endMetadata()
                    .withNewSpec()
                        .withSessionAffinity("ClientIP")
                        .withSelector(serviceSelector)
                        .addNewPort()
                            .withName("proxy")
                            .withPort(proxyPort)
                            .withNewTargetPort(proxyPort)
                        .endPort()
                    .endSpec()
                    .build();

            client.services().inNamespace(namespace).resource(newService).create();
        }

        return UpdateControl.noUpdate();
    }
}
