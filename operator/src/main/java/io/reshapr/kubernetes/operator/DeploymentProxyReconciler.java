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
    
    public static final int JGROUPS_PORT = 7778;
    public static final int JGROUPS_FD_PORT = 57778;

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
            logger.infof("Creating dedicated headless Service %s in namespace %s", serviceName, namespace);

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
                        .withClusterIP("None") // Headless service
                        .withSelector(serviceSelector)
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

            client.services().inNamespace(namespace).resource(newService).create();
        }

        return UpdateControl.noUpdate();
    }
}
