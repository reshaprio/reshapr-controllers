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

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.javaoperatorsdk.webhook.admission.AdmissionController;
import io.javaoperatorsdk.webhook.admission.NotAllowedException;
import io.javaoperatorsdk.webhook.admission.Operation;
import io.javaoperatorsdk.webhook.admission.mutation.Mutator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory and mutator logic for the reShapr mutating admission webhook.
 * <p>
 * The {@link PodMutator} injects a reShapr proxy sidecar container into Pods carrying the
 * {@code io.reshapr/inject=true} annotation. Configuration values that cannot be computed from the
 * Pod itself are resolved with the following precedence (highest first):
 * <ol>
 *    <li>an explicit annotation on the Pod (propagated from the Deployment pod template),</li>
 *    <li>a namespace-local Secret ({@value #DEFAULT_CONFIG_SECRET_NAME} by default, overridable via
 *        the {@value #CONFIG_SECRET_NAME_ANNOTATION} annotation).</li>
 * </ol>
 * Sensitive values (control-plane token and cluster keystore passwords) are always sourced from the
 * Secret through {@code secretKeyRef} references so they never appear in clear text in the Pod spec.
 *
 * @author vaishnav
 * @author laurent
 */
public class AdmissionControllers {

   // -- Trigger and routing --------------------------------------------------------------------
   public static final String INJECT_ANNOTATION = "io.reshapr/inject";
   public static final String PROXY_INJECTED_LABEL = "reshapr.io/proxy-injected";

   // -- Configuration override annotations -----------------------------------------------------
   public static final String CONFIG_SECRET_NAME_ANNOTATION = "io.reshapr/config-secret-name";
   public static final String GATEWAY_ID_PREFIX_ANNOTATION = "io.reshapr/gateway-id-prefix";
   public static final String GATEWAY_FQDNS_ANNOTATION = "io.reshapr/gateway-fqdns";
   public static final String GATEWAY_LABELS_ANNOTATION = "io.reshapr/gateway-labels";
   public static final String CONTROL_PLANE_HOST_ANNOTATION = "io.reshapr/control-plane-host";
   public static final String CONTROL_PLANE_PORT_ANNOTATION = "io.reshapr/control-plane-port";
   public static final String CONTROL_PLANE_TLS_PLAINTEXT_ANNOTATION = "io.reshapr/control-plane-tls-plaintext";
   public static final String PROXY_IMAGE_ANNOTATION = "io.reshapr/proxy-image";
   // Opt-in (enabled by default) for the dedicated ClusterIP Service exposing the MCP endpoint.
   public static final String EXPOSE_MCP_ANNOTATION = "io.reshapr/expose-mcp";

   // -- Namespace-local configuration Secret ---------------------------------------------------
   public static final String DEFAULT_CONFIG_SECRET_NAME = "reshapr-proxy-config";
   public static final String SECRET_KEY_GATEWAY_FQDNS = "gateway-fqdns";
   public static final String SECRET_KEY_GATEWAY_LABELS = "gateway-labels";
   public static final String SECRET_KEY_CTRL_HOST = "control-plane-host";
   public static final String SECRET_KEY_CTRL_PORT = "control-plane-port";
   public static final String SECRET_KEY_CTRL_TLS_PLAINTEXT = "control-plane-tls-plaintext";
   public static final String SECRET_KEY_CTRL_TOKEN = "control-plane-token";
   public static final String SECRET_KEY_CLUSTER_STORE_PASSWORD = "cluster-store-password";
   public static final String SECRET_KEY_CLUSTER_KEY_PASSWORD = "cluster-key-password";

   // -- Cluster keystore (JGroups SYM_ENCRYPT) -------------------------------------------------
   public static final String KEYSTORE_SECRET_KEY = "reshapr-cluster.jceks";
   public static final String KEYSTORE_VOLUME_NAME = "reshapr-cluster-keystore";
   public static final String KEYSTORE_MOUNT_PATH = "/etc/reshapr/keystore";
   public static final String KEYSTORE_ALIAS = "reshapr-cluster";

   // -- Injected proxy container ---------------------------------------------------------------
   public static final String PROXY_CONTAINER_NAME = "reshapr-proxy";
   public static final String DEFAULT_PROXY_IMAGE = "registry.reshapr.io/reshapr/reshapr-proxy:nightly";
   public static final int PROXY_HTTP_PORT = 7777;
   public static final int JGROUPS_PORT = 7778;
   public static final int JGROUPS_FD_PORT = 57778;

   private static final String DEFAULT_NAMESPACE = "reshapr-system";

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

         if (annotations == null || !"true".equalsIgnoreCase(annotations.get(INJECT_ANNOTATION))) {
            return resource;
         }

         // Idempotency: skip if the proxy sidecar has already been injected.
         if (resource.getSpec().getContainers() != null &&
               resource.getSpec().getContainers().stream()
                     .anyMatch(c -> PROXY_CONTAINER_NAME.equals(c.getName()))) {
            return resource;
         }

         String namespace = resource.getMetadata().getNamespace();
         if (namespace == null) {
            namespace = DEFAULT_NAMESPACE;
         }

         String configSecretName = annotationOr(annotations, CONFIG_SECRET_NAME_ANNOTATION, DEFAULT_CONFIG_SECRET_NAME);
         String deploymentName = resolveDeploymentName(resource);
         String dnsQuery = "reshapr-proxy-" + deploymentName + "." + namespace + ".svc.cluster.local";

         List<EnvVar> envVars = buildEnvVars(annotations, configSecretName, dnsQuery);

         String proxyImage = annotationOr(annotations, PROXY_IMAGE_ANNOTATION, DEFAULT_PROXY_IMAGE);
         ContainerBuilder proxyBuilder = new ContainerBuilder()
               .withName(PROXY_CONTAINER_NAME)
               .withImage(proxyImage)
               .withEnv(envVars)
               .withVolumeMounts(keystoreVolumeMount())
               .addNewPort()
                  .withContainerPort(PROXY_HTTP_PORT)
                  .withName("proxy")
               .endPort()
               .addNewPort()
                  .withContainerPort(JGROUPS_PORT)
                  .withName("jgroups")
               .endPort()
               .addNewPort()
                  .withContainerPort(JGROUPS_FD_PORT)
                  .withName("jgroups-fd")
               .endPort();

         // Add the sidecar container to the Pod.
         if (resource.getSpec().getContainers() == null) {
            resource.getSpec().setContainers(new ArrayList<>());
         }
         resource.getSpec().getContainers().add(proxyBuilder.build());

         // Mount the cluster keystore from the configuration Secret.
         if (resource.getSpec().getVolumes() == null) {
            resource.getSpec().setVolumes(new ArrayList<>());
         }
         resource.getSpec().getVolumes().add(keystoreVolume(configSecretName));

         // Add the routing label so the headless Service selects this Pod.
         if (resource.getMetadata().getLabels() == null) {
            resource.getMetadata().setLabels(new HashMap<>());
         }
         resource.getMetadata().getLabels().put(PROXY_INJECTED_LABEL, "true");

         return resource;
      }
   }

   /**
    * Builds the environment variables injected into the proxy sidecar. Ordering matters: variables
    * referenced through {@code $(VAR)} expansion (POD_NAME, POD_IP, cluster passwords) are declared
    * before the variables that consume them (RESHAPR_GATEWAY_ID, JAVA_OPTS_APPEND).
    */
   private static List<EnvVar> buildEnvVars(Map<String, String> annotations, String configSecretName, String dnsQuery) {
      List<EnvVar> envVars = new ArrayList<>();

      // Downward API references consumed by later variables.
      envVars.add(fieldRefEnvVar("POD_NAME", "metadata.name"));
      envVars.add(fieldRefEnvVar("POD_IP", "status.podIP"));

      // Cluster keystore passwords (always from the Secret, referenced by JAVA_OPTS_APPEND).
      envVars.add(secretKeyRefEnvVar("RESHAPR_CLUSTER_STORE_PASSWORD", configSecretName, SECRET_KEY_CLUSTER_STORE_PASSWORD, false));
      envVars.add(secretKeyRefEnvVar("RESHAPR_CLUSTER_KEY_PASSWORD", configSecretName, SECRET_KEY_CLUSTER_KEY_PASSWORD, false));

      // Gateway identity — unique per Pod. metadata.name is only populated after admission for
      // Pods created with generateName, so we defer to a runtime $(POD_NAME) reference.
      String idPrefix = annotations.get(GATEWAY_ID_PREFIX_ANNOTATION);
      String gatewayId = (idPrefix != null && !idPrefix.isBlank()) ? idPrefix + "-$(POD_NAME)" : "$(POD_NAME)";
      envVars.add(valueEnvVar("RESHAPR_GATEWAY_ID", gatewayId));

      // Non-sensitive configuration: annotation overrides Secret, Secret key is optional.
      envVars.add(annotationOrSecretEnvVar("RESHAPR_GATEWAY_FQDNS", annotations, GATEWAY_FQDNS_ANNOTATION, configSecretName, SECRET_KEY_GATEWAY_FQDNS));
      envVars.add(annotationOrSecretEnvVar("RESHAPR_GATEWAY_LABELS", annotations, GATEWAY_LABELS_ANNOTATION, configSecretName, SECRET_KEY_GATEWAY_LABELS));
      envVars.add(annotationOrSecretEnvVar("RESHAPR_CTRL_HOST", annotations, CONTROL_PLANE_HOST_ANNOTATION, configSecretName, SECRET_KEY_CTRL_HOST));
      envVars.add(annotationOrSecretEnvVar("RESHAPR_CTRL_PORT", annotations, CONTROL_PLANE_PORT_ANNOTATION, configSecretName, SECRET_KEY_CTRL_PORT));
      envVars.add(annotationOrSecretEnvVar("RESHAPR_CTRL_TLS_PLAINTEXT", annotations, CONTROL_PLANE_TLS_PLAINTEXT_ANNOTATION, configSecretName, SECRET_KEY_CTRL_TLS_PLAINTEXT));

      // Control-plane token — always from the Secret.
      envVars.add(secretKeyRefEnvVar("RESHAPR_CTRL_TOKEN", configSecretName, SECRET_KEY_CTRL_TOKEN, false));

      // JVM options: HTTP host, Java 25 preview, and Infinispan/JGroups clustering (SYM_ENCRYPT).
      String javaOpts = "-XX:+UseCompactObjectHeaders " +
            "-Dquarkus.http.host=0.0.0.0 " +
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager " +
            "--enable-preview " +
            "-Dreshapr.infinispan.stack=reshapr-k8s " +
            "-Dreshapr.infinispan.dns-query=" + dnsQuery + " " +
            "-Djgroups.port_range=0 " +
            "-Djgroups.bind.address=$(POD_IP) " +
            "-Dreshapr.infinispan.encrypt.keystore=" + KEYSTORE_MOUNT_PATH + "/" + KEYSTORE_SECRET_KEY + " " +
            "-Dreshapr.infinispan.encrypt.store-password=$(RESHAPR_CLUSTER_STORE_PASSWORD) " +
            "-Dreshapr.infinispan.encrypt.key-password=$(RESHAPR_CLUSTER_KEY_PASSWORD) " +
            "-Dreshapr.infinispan.encrypt.alias=" + KEYSTORE_ALIAS;
      envVars.add(valueEnvVar("JAVA_OPTS_APPEND", javaOpts));

      // OpenTelemetry SDK disabled flag for the moment.
      envVars.add(valueEnvVar("QUARKUS_OTEL_SDK_DISABLED", "true"));

      return envVars;
   }

   /**
    * Resolves the owning Deployment name so the sidecar can join the matching headless Service.
    * Strips the ReplicaSet hash suffix when the Pod is owned by a ReplicaSet.
    */
   private static String resolveDeploymentName(Pod resource) {
      List<OwnerReference> owners = resource.getMetadata().getOwnerReferences();
      if (owners != null && !owners.isEmpty()) {
         OwnerReference owner = owners.get(0);
         if ("ReplicaSet".equals(owner.getKind())) {
            String rsName = owner.getName();
            int lastDash = rsName.lastIndexOf('-');
            return lastDash > 0 ? rsName.substring(0, lastDash) : rsName;
         }
         return owner.getName();
      }
      Map<String, String> labels = resource.getMetadata().getLabels();
      if (labels != null && labels.containsKey("app")) {
         return labels.get("app");
      }
      return "unknown";
   }

   private static VolumeMount keystoreVolumeMount() {
      return new VolumeMountBuilder()
            .withName(KEYSTORE_VOLUME_NAME)
            .withMountPath(KEYSTORE_MOUNT_PATH)
            .withReadOnly(true)
            .build();
   }

   private static Volume keystoreVolume(String configSecretName) {
      return new VolumeBuilder()
            .withName(KEYSTORE_VOLUME_NAME)
            .withNewSecret()
               .withSecretName(configSecretName)
               .addNewItem()
                  .withKey(KEYSTORE_SECRET_KEY)
                  .withPath(KEYSTORE_SECRET_KEY)
               .endItem()
            .endSecret()
            .build();
   }

   private static String annotationOr(Map<String, String> annotations, String key, String defaultValue) {
      String value = annotations.get(key);
      return (value != null && !value.isBlank()) ? value : defaultValue;
   }

   private static EnvVar valueEnvVar(String name, String value) {
      return new EnvVarBuilder().withName(name).withValue(value).build();
   }

   private static EnvVar fieldRefEnvVar(String name, String fieldPath) {
      return new EnvVarBuilder()
            .withName(name)
            .withNewValueFrom()
               .withNewFieldRef().withFieldPath(fieldPath).endFieldRef()
            .endValueFrom()
            .build();
   }

   private static EnvVar secretKeyRefEnvVar(String name, String secretName, String secretKey, boolean optional) {
      return new EnvVarBuilder()
            .withName(name)
            .withNewValueFrom()
               .withNewSecretKeyRef()
                  .withName(secretName)
                  .withKey(secretKey)
                  .withOptional(optional)
               .endSecretKeyRef()
            .endValueFrom()
            .build();
   }

   /**
    * Returns a literal env var from the annotation when present, otherwise an optional
    * {@code secretKeyRef} so the proxy falls back to its built-in default if the key is absent.
    */
   private static EnvVar annotationOrSecretEnvVar(String name, Map<String, String> annotations, String annotationKey,
                                                  String secretName, String secretKey) {
      String value = annotations.get(annotationKey);
      if (value != null && !value.isBlank()) {
         return valueEnvVar(name, value);
      }
      return secretKeyRefEnvVar(name, secretName, secretKey, true);
   }
}
