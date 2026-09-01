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

/**
 * Companion reconciler to the {@link AdmissionControllers.PodMutator}. For Deployments whose pod
 * template opts into injection it provisions:
 * <ul>
 *    <li>a headless Service ({@code reshapr-proxy-<deployment>}) used by the injected proxy sidecars
 *        for JGroups DNS_PING cluster discovery, and</li>
 *    <li>a ClusterIP Service ({@code reshapr-proxy-<deployment>-mcp}) exposing the MCP endpoint —
 *        enabled by default, disable with {@code io.reshapr/expose-mcp: "false"}.</li>
 * </ul>
 * These Services cannot be created from the mutating webhook itself because it is declared
 * {@code sideEffects: None}, so this reconciler owns that side effect.
 *
 * @author vaishnav
 */
@ControllerConfiguration
public class DeploymentProxyReconciler implements Reconciler<Deployment> {

    private static final Logger logger = Logger.getLogger(DeploymentProxyReconciler.class);

    public static final String INJECT_ANNOTATION = AdmissionControllers.INJECT_ANNOTATION;
    public static final String EXPOSE_MCP_ANNOTATION = AdmissionControllers.EXPOSE_MCP_ANNOTATION;
    public static final String PROXY_INJECTED_LABEL = AdmissionControllers.PROXY_INJECTED_LABEL;

    public static final int MCP_PORT = AdmissionControllers.PROXY_HTTP_PORT;
    public static final int JGROUPS_PORT = AdmissionControllers.JGROUPS_PORT;
    public static final int JGROUPS_FD_PORT = AdmissionControllers.JGROUPS_FD_PORT;

    private final KubernetesClient client;

    public DeploymentProxyReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<Deployment> reconcile(Deployment deployment, Context<Deployment> context) {
        // Single source of truth: the pod template annotation, which is also what the webhook sees
        // on the created Pods. The Deployment does not need its own metadata annotation.
        Map<String, String> annotations = podTemplateAnnotations(deployment);
        String namespace = deployment.getMetadata().getNamespace();
        if (namespace == null) {
            namespace = "reshapr-system";
        }

        String deploymentName = deployment.getMetadata().getName();
        String discoveryServiceName = "reshapr-proxy-" + deploymentName;
        String mcpServiceName = discoveryServiceName + "-mcp";

        boolean injected = annotations != null && "true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION));
        if (!injected) {
            deleteServiceIfExists(namespace, discoveryServiceName);
            deleteServiceIfExists(namespace, mcpServiceName);
            return UpdateControl.noUpdate();
        }

        logger.infof("Reconciling Deployment %s/%s for Reshapr Proxy Services", namespace, deploymentName);

        Map<String, String> selector = proxySelector(deployment);

        // Headless Service for JGroups DNS_PING cluster discovery.
        ensureService(namespace, buildHeadlessService(discoveryServiceName, namespace, deployment, selector));

        // Dedicated ClusterIP Service exposing the MCP endpoint. Opt-in enabled by default.
        boolean exposeMcp = !"false".equalsIgnoreCase(annotations.get(EXPOSE_MCP_ANNOTATION));
        if (exposeMcp) {
            ensureService(namespace, buildMcpService(mcpServiceName, namespace, deployment, selector));
        } else {
            deleteServiceIfExists(namespace, mcpServiceName);
        }

        return UpdateControl.noUpdate();
    }

    private void ensureService(String namespace, Service desired) {
        String name = desired.getMetadata().getName();
        if (client.services().inNamespace(namespace).withName(name).get() == null) {
            logger.infof("Creating Service %s in namespace %s", name, namespace);
            client.services().inNamespace(namespace).resource(desired).create();
        }
    }

    private void deleteServiceIfExists(String namespace, String name) {
        if (client.services().inNamespace(namespace).withName(name).get() != null) {
            logger.infof("Injection disabled. Deleting Service %s in namespace %s", name, namespace);
            client.services().inNamespace(namespace).withName(name).delete();
        }
    }

    /** Selector matching the injected proxy sidecars: the routing label plus the Deployment's own labels. */
    private static Map<String, String> proxySelector(Deployment deployment) {
        Map<String, String> selector = new HashMap<>();
        selector.put(PROXY_INJECTED_LABEL, "true");
        if (deployment.getSpec() != null && deployment.getSpec().getSelector() != null
                && deployment.getSpec().getSelector().getMatchLabels() != null) {
            selector.putAll(deployment.getSpec().getSelector().getMatchLabels());
        }
        return selector;
    }

    private static Service buildHeadlessService(String name, String namespace, Deployment deployment, Map<String, String> selector) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    // Owner reference so the Service is garbage-collected with the Deployment.
                    .addNewOwnerReference()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(deployment.getMetadata().getName())
                        .withUid(deployment.getMetadata().getUid())
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                    .withClusterIP("None") // Headless service for DNS_PING discovery
                    .withSelector(selector)
                    .addNewPort()
                        .withName("jgroups")
                        .withPort(JGROUPS_PORT)
                        .withNewTargetPort(JGROUPS_PORT)
                    .endPort()
                    .addNewPort()
                        .withName("jgroups-fd")
                        .withPort(JGROUPS_FD_PORT)
                        .withNewTargetPort(JGROUPS_FD_PORT)
                    .endPort()
                .endSpec()
                .build();
    }

    private static Service buildMcpService(String name, String namespace, Deployment deployment, Map<String, String> selector) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    // Owner reference so the Service is garbage-collected with the Deployment.
                    .addNewOwnerReference()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(deployment.getMetadata().getName())
                        .withUid(deployment.getMetadata().getUid())
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(selector)
                    .addNewPort()
                        .withName("mcp")
                        .withPort(MCP_PORT)
                        .withNewTargetPort(MCP_PORT)
                    .endPort()
                .endSpec()
                .build();
    }

    /** Reads the injection annotations from the Deployment's pod template. */
    private static Map<String, String> podTemplateAnnotations(Deployment deployment) {
        if (deployment.getSpec() != null
                && deployment.getSpec().getTemplate() != null
                && deployment.getSpec().getTemplate().getMetadata() != null) {
            return deployment.getSpec().getTemplate().getMetadata().getAnnotations();
        }
        return null;
    }
}
