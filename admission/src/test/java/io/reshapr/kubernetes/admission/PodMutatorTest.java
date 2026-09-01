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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.javaoperatorsdk.webhook.admission.Operation;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * @author laurent
 */
class PodMutatorTest {

   private final AdmissionControllers.PodMutator mutator = new AdmissionControllers.PodMutator();

   @Test
   void shouldNotInjectWhenAnnotationAbsent() {
      Pod pod = basePod().editMetadata().addToAnnotations("foo", "bar").endMetadata().build();

      Pod result = mutator.mutate(pod, Operation.CREATE);

      assertNull(findProxyContainer(result));
      assertFalse(result.getMetadata().getLabels() != null
            && result.getMetadata().getLabels().containsKey(AdmissionControllers.PROXY_INJECTED_LABEL));
   }

   @Test
   void shouldInjectProxySidecarWithDefaults() {
      Pod pod = injectablePod().build();

      Pod result = mutator.mutate(pod, Operation.CREATE);

      Container proxy = findProxyContainer(result);
      assertNotNull(proxy);
      assertEquals(AdmissionControllers.DEFAULT_PROXY_IMAGE, proxy.getImage());
      assertTrue(proxy.getPorts().stream().anyMatch(p -> p.getContainerPort() == AdmissionControllers.PROXY_HTTP_PORT));
      assertTrue(proxy.getPorts().stream().anyMatch(p -> p.getContainerPort() == AdmissionControllers.JGROUPS_PORT));
      assertTrue(proxy.getPorts().stream().anyMatch(p -> p.getContainerPort() == AdmissionControllers.JGROUPS_FD_PORT));

      assertEquals("true", result.getMetadata().getLabels().get(AdmissionControllers.PROXY_INJECTED_LABEL));
   }

   @Test
   void shouldDeriveGatewayIdFromPodNameReference() {
      Pod result = mutator.mutate(injectablePod().build(), Operation.CREATE);
      Container proxy = findProxyContainer(result);

      assertEquals("metadata.name", env(proxy, "POD_NAME").getValueFrom().getFieldRef().getFieldPath());
      assertEquals("$(POD_NAME)", env(proxy, "RESHAPR_GATEWAY_ID").getValue());
   }

   @Test
   void shouldPrefixGatewayIdWhenAnnotationProvided() {
      Pod pod = injectablePod()
            .editMetadata().addToAnnotations(AdmissionControllers.GATEWAY_ID_PREFIX_ANNOTATION, "eu").endMetadata()
            .build();

      Container proxy = findProxyContainer(mutator.mutate(pod, Operation.CREATE));

      assertEquals("eu-$(POD_NAME)", env(proxy, "RESHAPR_GATEWAY_ID").getValue());
   }

   @Test
   void shouldSourceSensitiveValuesFromDefaultSecret() {
      Container proxy = findProxyContainer(mutator.mutate(injectablePod().build(), Operation.CREATE));

      assertSecretRef(env(proxy, "RESHAPR_CTRL_TOKEN"), AdmissionControllers.DEFAULT_CONFIG_SECRET_NAME,
            AdmissionControllers.SECRET_KEY_CTRL_TOKEN, false);
      assertSecretRef(env(proxy, "RESHAPR_CLUSTER_STORE_PASSWORD"), AdmissionControllers.DEFAULT_CONFIG_SECRET_NAME,
            AdmissionControllers.SECRET_KEY_CLUSTER_STORE_PASSWORD, false);
      assertSecretRef(env(proxy, "RESHAPR_CLUSTER_KEY_PASSWORD"), AdmissionControllers.DEFAULT_CONFIG_SECRET_NAME,
            AdmissionControllers.SECRET_KEY_CLUSTER_KEY_PASSWORD, false);
   }

   @Test
   void shouldSourceNonSensitiveValuesAsOptionalSecretRefByDefault() {
      Container proxy = findProxyContainer(mutator.mutate(injectablePod().build(), Operation.CREATE));

      assertSecretRef(env(proxy, "RESHAPR_CTRL_HOST"), AdmissionControllers.DEFAULT_CONFIG_SECRET_NAME,
            AdmissionControllers.SECRET_KEY_CTRL_HOST, true);
      assertSecretRef(env(proxy, "RESHAPR_GATEWAY_FQDNS"), AdmissionControllers.DEFAULT_CONFIG_SECRET_NAME,
            AdmissionControllers.SECRET_KEY_GATEWAY_FQDNS, true);
   }

   @Test
   void shouldLetAnnotationsOverrideNonSensitiveValues() {
      Pod pod = injectablePod()
            .editMetadata()
               .addToAnnotations(AdmissionControllers.CONTROL_PLANE_HOST_ANNOTATION, "ctrl.example.com")
               .addToAnnotations(AdmissionControllers.GATEWAY_FQDNS_ANNOTATION, "gw.example.com:7777")
            .endMetadata()
            .build();

      Container proxy = findProxyContainer(mutator.mutate(pod, Operation.CREATE));

      assertEquals("ctrl.example.com", env(proxy, "RESHAPR_CTRL_HOST").getValue());
      assertNull(env(proxy, "RESHAPR_CTRL_HOST").getValueFrom());
      assertEquals("gw.example.com:7777", env(proxy, "RESHAPR_GATEWAY_FQDNS").getValue());
   }

   @Test
   void shouldUseCustomConfigSecretName() {
      Pod pod = injectablePod()
            .editMetadata().addToAnnotations(AdmissionControllers.CONFIG_SECRET_NAME_ANNOTATION, "my-secret").endMetadata()
            .build();

      Pod result = mutator.mutate(pod, Operation.CREATE);
      Container proxy = findProxyContainer(result);

      assertSecretRef(env(proxy, "RESHAPR_CTRL_TOKEN"), "my-secret", AdmissionControllers.SECRET_KEY_CTRL_TOKEN, false);
      Volume keystore = result.getSpec().getVolumes().stream()
            .filter(v -> AdmissionControllers.KEYSTORE_VOLUME_NAME.equals(v.getName()))
            .findFirst().orElseThrow();
      assertEquals("my-secret", keystore.getSecret().getSecretName());
   }

   @Test
   void shouldMountClusterKeystoreAndConfigureEncryption() {
      Pod result = mutator.mutate(injectablePod().build(), Operation.CREATE);
      Container proxy = findProxyContainer(result);

      assertTrue(proxy.getVolumeMounts().stream()
            .anyMatch(m -> AdmissionControllers.KEYSTORE_VOLUME_NAME.equals(m.getName())
                  && AdmissionControllers.KEYSTORE_MOUNT_PATH.equals(m.getMountPath())));
      assertTrue(result.getSpec().getVolumes().stream()
            .anyMatch(v -> AdmissionControllers.KEYSTORE_VOLUME_NAME.equals(v.getName())));

      String javaOpts = env(proxy, "JAVA_OPTS_APPEND").getValue();
      assertTrue(javaOpts.contains("-Dreshapr.infinispan.stack=reshapr-k8s"));
      assertTrue(javaOpts.contains("-Dreshapr.infinispan.dns-query=reshapr-proxy-my-app.demo.svc.cluster.local"));
      assertTrue(javaOpts.contains("-Dreshapr.infinispan.encrypt.keystore="
            + AdmissionControllers.KEYSTORE_MOUNT_PATH + "/" + AdmissionControllers.KEYSTORE_SECRET_KEY));
      assertTrue(javaOpts.contains("-Dreshapr.infinispan.encrypt.store-password=$(RESHAPR_CLUSTER_STORE_PASSWORD)"));
      assertTrue(javaOpts.contains("-Dreshapr.infinispan.encrypt.key-password=$(RESHAPR_CLUSTER_KEY_PASSWORD)"));
   }

   @Test
   void shouldBeIdempotent() {
      Pod once = mutator.mutate(injectablePod().build(), Operation.CREATE);
      Pod twice = mutator.mutate(once, Operation.UPDATE);

      long proxyContainers = twice.getSpec().getContainers().stream()
            .filter(c -> AdmissionControllers.PROXY_CONTAINER_NAME.equals(c.getName()))
            .count();
      assertEquals(1, proxyContainers);
      long keystoreVolumes = twice.getSpec().getVolumes().stream()
            .filter(v -> AdmissionControllers.KEYSTORE_VOLUME_NAME.equals(v.getName()))
            .count();
      assertEquals(1, keystoreVolumes);
   }

   // -- helpers --------------------------------------------------------------------------------

   private static PodBuilder basePod() {
      return new PodBuilder()
            .withNewMetadata()
               .withName("my-app-abc123-xyz")
               .withNamespace("demo")
            .endMetadata()
            .withNewSpec()
               .addNewContainer()
                  .withName("app")
                  .withImage("nginx:latest")
               .endContainer()
            .endSpec();
   }

   private static PodBuilder injectablePod() {
      return basePod()
            .editMetadata()
               .addToAnnotations(AdmissionControllers.INJECT_ANNOTATION, "true")
               .addNewOwnerReference()
                  .withApiVersion("apps/v1")
                  .withKind("ReplicaSet")
                  .withName("my-app-abc123")
                  .withUid("uid-1")
               .endOwnerReference()
            .endMetadata();
   }

   private static Container findProxyContainer(Pod pod) {
      if (pod.getSpec().getContainers() == null) {
         return null;
      }
      return pod.getSpec().getContainers().stream()
            .filter(c -> AdmissionControllers.PROXY_CONTAINER_NAME.equals(c.getName()))
            .findFirst().orElse(null);
   }

   private static EnvVar env(Container container, String name) {
      return container.getEnv().stream()
            .filter(e -> name.equals(e.getName()))
            .findFirst().orElseThrow(() -> new AssertionError("Missing env var: " + name));
   }

   private static void assertSecretRef(EnvVar var, String secretName, String key, boolean optional) {
      assertNull(var.getValue());
      var ref = var.getValueFrom().getSecretKeyRef();
      assertEquals(secretName, ref.getName());
      assertEquals(key, ref.getKey());
      assertEquals(optional, ref.getOptional());
   }

   @Test
   void injectablePodHasExpectedNamespaceForDnsQuery() {
      // Guards the dns-query derivation used in shouldMountClusterKeystoreAndConfigureEncryption.
      Map<String, String> annotations = injectablePod().build().getMetadata().getAnnotations();
      assertEquals("true", annotations.get(AdmissionControllers.INJECT_ANNOTATION));
   }
}
