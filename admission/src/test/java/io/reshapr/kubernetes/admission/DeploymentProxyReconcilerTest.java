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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author laurent
 */
@EnableKubernetesMockClient(crud = true)
class DeploymentProxyReconcilerTest {

   private static final String NS = "demo";
   private static final String DEPLOYMENT = "my-app";
   private static final String DISCOVERY_SVC = "reshapr-proxy-my-app";
   private static final String MCP_SVC = "reshapr-proxy-my-app-mcp";

   // Injected by the Fabric8 mock server extension. Non-static so a fresh server (and clean
   // state) is created for each test method.
   KubernetesClient client;

   private DeploymentProxyReconciler reconciler;

   @BeforeEach
   void setUp() {
      reconciler = new DeploymentProxyReconciler(client);
   }

   @Test
   void shouldProvisionBothServicesWhenInjected() {
      reconciler.reconcile(injectableDeployment(null), null);

      Service discovery = service(DISCOVERY_SVC);
      assertNotNull(discovery);
      assertEquals("None", discovery.getSpec().getClusterIP());
      assertTrue(discovery.getSpec().getPorts().stream().anyMatch(p -> p.getPort() == DeploymentProxyReconciler.JGROUPS_PORT));
      assertTrue(discovery.getSpec().getPorts().stream().anyMatch(p -> p.getPort() == DeploymentProxyReconciler.JGROUPS_FD_PORT));

      Service mcp = service(MCP_SVC);
      assertNotNull(mcp);
      assertEquals("ClusterIP", mcp.getSpec().getType());
      assertTrue(mcp.getSpec().getPorts().stream().anyMatch(p -> p.getPort() == DeploymentProxyReconciler.MCP_PORT));
   }

   @Test
   void shouldSetSelectorAndOwnerReferenceOnServices() {
      reconciler.reconcile(injectableDeployment(null), null);

      Service mcp = service(MCP_SVC);
      assertEquals("true", mcp.getSpec().getSelector().get(DeploymentProxyReconciler.PROXY_INJECTED_LABEL));
      assertEquals(DEPLOYMENT, mcp.getSpec().getSelector().get("app"));
      assertEquals(1, mcp.getMetadata().getOwnerReferences().size());
      assertEquals(DEPLOYMENT, mcp.getMetadata().getOwnerReferences().get(0).getName());
      assertEquals("Deployment", mcp.getMetadata().getOwnerReferences().get(0).getKind());
   }

   @Test
   void shouldNotProvisionMcpServiceWhenExposeMcpDisabled() {
      reconciler.reconcile(injectableDeployment("false"), null);

      assertNotNull(service(DISCOVERY_SVC));
      assertNull(service(MCP_SVC));
   }

   @Test
   void shouldDeleteMcpServiceWhenExposeMcpToggledOff() {
      reconciler.reconcile(injectableDeployment(null), null);
      assertNotNull(service(MCP_SVC));

      reconciler.reconcile(injectableDeployment("false"), null);

      assertNotNull(service(DISCOVERY_SVC));
      assertNull(service(MCP_SVC));
   }

   @Test
   void shouldDeleteBothServicesWhenInjectionAbsent() {
      client.services().inNamespace(NS).resource(dummyService(DISCOVERY_SVC)).create();
      client.services().inNamespace(NS).resource(dummyService(MCP_SVC)).create();

      reconciler.reconcile(bareDeployment(), null);

      assertNull(service(DISCOVERY_SVC));
      assertNull(service(MCP_SVC));
   }

   @Test
   void shouldBeIdempotent() {
      reconciler.reconcile(injectableDeployment(null), null);
      reconciler.reconcile(injectableDeployment(null), null);

      assertNotNull(service(DISCOVERY_SVC));
      assertNotNull(service(MCP_SVC));
   }

   // -- helpers --------------------------------------------------------------------------------

   private Service service(String name) {
      return client.services().inNamespace(NS).withName(name).get();
   }

   private static Deployment bareDeployment() {
      return new DeploymentBuilder()
            .withNewMetadata()
               .withName(DEPLOYMENT)
               .withNamespace(NS)
               .withUid("uid-1")
            .endMetadata()
            .withNewSpec()
               .withNewSelector().addToMatchLabels("app", DEPLOYMENT).endSelector()
               .withNewTemplate()
                  .withNewMetadata().endMetadata()
               .endTemplate()
            .endSpec()
            .build();
   }

   private static Deployment injectableDeployment(String exposeMcp) {
      DeploymentBuilder builder = new DeploymentBuilder(bareDeployment());
      builder.editSpec().editTemplate().editMetadata()
            .addToAnnotations(DeploymentProxyReconciler.INJECT_ANNOTATION, "true")
            .endMetadata().endTemplate().endSpec();
      if (exposeMcp != null) {
         builder.editSpec().editTemplate().editMetadata()
               .addToAnnotations(DeploymentProxyReconciler.EXPOSE_MCP_ANNOTATION, exposeMcp)
               .endMetadata().endTemplate().endSpec();
      }
      return builder.build();
   }

   private static Service dummyService(String name) {
      return new ServiceBuilder()
            .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
            .withNewSpec().withClusterIP("None").endSpec()
            .build();
   }
}
