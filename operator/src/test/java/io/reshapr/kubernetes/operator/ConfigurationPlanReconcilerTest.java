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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.reshapr.client.ApiClient;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.Secret;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.CachePolicy;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.ConfigurationPlan;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.ConfigurationPlanSpec;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.HeaderPolicy;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.HeaderRename;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.HeaderRules;
import io.reshapr.kubernetes.api.configurationplan.v1alpha1.OAuth2Spec;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigurationPlanReconciler}.
 * Validates that every property set on the ConfigurationPlan spec is properly
 * forwarded to the control plane when creating the remote ConfigurationPlan.
 * Uses Mockito's {@link MockedConstruction} to intercept the {@link DefaultApi}
 * instantiated inside the reconciler and capture the payload sent to
 * {@link DefaultApi#createConfigPlan}.
 *
 * @author laurent
 */
class ConfigurationPlanReconcilerTest {

   private static final String ORG = "reshapr";
   private static final String REMOTE_SERVICE_ID = "svc-1234";
   private static final String REMOTE_PLAN_ID = "plan-5678";
   private static final String REMOTE_SECRET_ID = "sec-9999";

   // ── helpers ──────────────────────────────────────────────────────────────

   private ConfigurationPlan buildFullSpecConfigurationPlan() {
      ServiceRef ref = new ServiceRef();
      ref.setName("API Pastries");
      ref.setVersion("0.0.1");

      OAuth2Spec oauth2 = new OAuth2Spec();
      oauth2.setClientId("pastries-gateway");
      oauth2.setClientSecret("change-me");

      CachePolicy cache = new CachePolicy();
      cache.setTtlMs(60000L);
      cache.setCacheScope("public");

      HeaderRename rename = new HeaderRename();
      rename.setFrom("X-Client-Id");
      rename.setTo("X-Consumer-Id");

      HeaderRules requestRules = new HeaderRules();
      requestRules.setAllow(List.of("X-Request-Id", "X-Forwarded-For"));
      requestRules.setDeny(List.of("X-Internal-Debug"));
      requestRules.setRename(List.of(rename));

      HeaderRules responseRules = new HeaderRules();
      responseRules.setAllow(List.of("X-Response-Id"));

      HeaderPolicy headerPolicy = new HeaderPolicy();
      headerPolicy.setRequest(requestRules);
      headerPolicy.setResponse(responseRules);

      ConfigurationPlanSpec spec = new ConfigurationPlanSpec();
      spec.setService(ref);
      spec.setBackendEndpoint("https://pastries.prod.acme.com");
      spec.setApiKey(true);
      spec.setOauth2(oauth2);
      spec.setAudit(true);
      spec.setIncludedOperations(List.of("listPastries", "getPastry"));
      spec.setExcludedOperations(List.of("deletePastry"));
      spec.setCachePolicy(cache);
      spec.setHeaderPolicy(headerPolicy);

      Map<String, String> annotations = new HashMap<>();
      annotations.put(ReshaprAnnotations.INSTANCE, "reshapr-control-plane-ctrl.reshapr-system");
      annotations.put(ReshaprAnnotations.ORGANIZATION, ORG);

      ObjectMeta meta = new ObjectMeta();
      meta.setName("pastries-prod-plan");
      meta.setNamespace("default");
      meta.setGeneration(1L);
      meta.setAnnotations(annotations);

      ConfigurationPlan cp = new ConfigurationPlan();
      cp.setMetadata(meta);
      cp.setSpec(spec);
      return cp;
   }

   private io.reshapr.client.model.Service remoteService() {
      io.reshapr.client.model.Service svc = new io.reshapr.client.model.Service();
      svc.setId(REMOTE_SERVICE_ID);
      svc.setName("API Pastries");
      svc.setVersion("0.0.1");
      return svc;
   }

   // ── tests ────────────────────────────────────────────────────────────────

   @Test
   void doReconcile_forwardsAllSpecPropertiesToControlPlane() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ConfigurationPlanReconciler reconciler = new ConfigurationPlanReconciler(factory);
      ApiClient apiClient = mock(ApiClient.class);
      ConfigurationPlan cp = buildFullSpecConfigurationPlan();

      ArgumentCaptor<io.reshapr.client.model.ConfigurationPlan> planCaptor =
            ArgumentCaptor.forClass(io.reshapr.client.model.ConfigurationPlan.class);
      ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> {
               when(mock.getServices(any(BigDecimal.class), any(BigDecimal.class)))
                     .thenReturn(List.of(remoteService()));

               Secret createdSecret = new Secret();
               createdSecret.setId(REMOTE_SECRET_ID);
               when(mock.createSecret(any(Secret.class))).thenReturn(createdSecret);

               io.reshapr.client.model.ConfigurationPlan created = new io.reshapr.client.model.ConfigurationPlan();
               created.setId(REMOTE_PLAN_ID);
               when(mock.createConfigPlan(any(io.reshapr.client.model.ConfigurationPlan.class)))
                     .thenReturn(created);
            })) {

         UpdateControl<ConfigurationPlan> result = reconciler.doReconcile(cp, null, apiClient);

         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).createSecret(secretCaptor.capture());
         verify(mockedApi).createConfigPlan(planCaptor.capture());
         verify(mockedApi).renewConfigPlanApiKey(REMOTE_PLAN_ID);

         // Secret payload created from oauth2 spec
         Secret sentSecret = secretCaptor.getValue();
         assertEquals(ORG, sentSecret.getOrganizationId());
         assertEquals("pastries-prod-plan-oauth2", sentSecret.getName());
         assertNotNull(sentSecret.getOauth2ClientConfiguration());
         assertEquals("pastries-gateway", sentSecret.getOauth2ClientConfiguration().getClientId());
         assertEquals("change-me", sentSecret.getOauth2ClientConfiguration().getClientSecret());

         // ConfigurationPlan payload: every spec field must be forwarded
         io.reshapr.client.model.ConfigurationPlan sentPlan = planCaptor.getValue();
         assertEquals(ORG, sentPlan.getOrganizationId());
         assertEquals("pastries-prod-plan", sentPlan.getName());
         assertEquals(REMOTE_SERVICE_ID, sentPlan.getServiceId());
         assertEquals("https://pastries.prod.acme.com", sentPlan.getBackendEndpoint());
         assertEquals(REMOTE_SECRET_ID, sentPlan.getBackendSecretId());
         assertEquals(Boolean.TRUE, sentPlan.getAudit());
         assertEquals(List.of("listPastries", "getPastry"), sentPlan.getIncludedOperations());
         assertEquals(List.of("deletePastry"), sentPlan.getExcludedOperations());

         assertNotNull(sentPlan.getCachePolicy());
         assertEquals(60000L, sentPlan.getCachePolicy().getTtlMs());
         assertEquals("public", sentPlan.getCachePolicy().getCacheScope());

         assertNotNull(sentPlan.getHeaderPolicy());
         assertNotNull(sentPlan.getHeaderPolicy().getRequest());
         assertEquals(List.of("X-Request-Id", "X-Forwarded-For"),
               sentPlan.getHeaderPolicy().getRequest().getAllow());
         assertEquals(List.of("X-Internal-Debug"),
               sentPlan.getHeaderPolicy().getRequest().getDeny());
         assertNotNull(sentPlan.getHeaderPolicy().getRequest().getRename());
         assertEquals(1, sentPlan.getHeaderPolicy().getRequest().getRename().size());
         assertEquals("X-Client-Id",
               sentPlan.getHeaderPolicy().getRequest().getRename().get(0).getFrom());
         assertEquals("X-Consumer-Id",
               sentPlan.getHeaderPolicy().getRequest().getRename().get(0).getTo());
         assertNotNull(sentPlan.getHeaderPolicy().getResponse());
         assertEquals(List.of("X-Response-Id"),
               sentPlan.getHeaderPolicy().getResponse().getAllow());

         // Status must reflect a successful reconciliation
         assertNotNull(cp.getStatus());
         assertEquals(REMOTE_PLAN_ID, cp.getStatus().getConfigurationPlanId());
         assertEquals(io.reshapr.kubernetes.api.model.Status.READY, cp.getStatus().getStatus());
         assertEquals(Long.valueOf(1L), cp.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_withMinimalSpec_doesNotEmitOptionalPolicies() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ConfigurationPlanReconciler reconciler = new ConfigurationPlanReconciler(factory);
      ApiClient apiClient = mock(ApiClient.class);

      ServiceRef ref = new ServiceRef();
      ref.setName("API Pastries");
      ref.setVersion("0.0.1");

      ConfigurationPlanSpec spec = new ConfigurationPlanSpec();
      spec.setService(ref);
      spec.setBackendEndpoint("https://pastries.prod.acme.com");
      // no oauth2, no audit override, no operations, no cache/header policy, apiKey defaults to false

      Map<String, String> annotations = new HashMap<>();
      annotations.put(ReshaprAnnotations.INSTANCE, "reshapr-ctrl.reshapr-system");
      annotations.put(ReshaprAnnotations.ORGANIZATION, ORG);
      ObjectMeta meta = new ObjectMeta();
      meta.setName("minimal-plan");
      meta.setNamespace("default");
      meta.setGeneration(1L);
      meta.setAnnotations(annotations);

      ConfigurationPlan cp = new ConfigurationPlan();
      cp.setMetadata(meta);
      cp.setSpec(spec);

      ArgumentCaptor<io.reshapr.client.model.ConfigurationPlan> planCaptor =
            ArgumentCaptor.forClass(io.reshapr.client.model.ConfigurationPlan.class);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> {
               when(mock.getServices(any(BigDecimal.class), any(BigDecimal.class)))
                     .thenReturn(List.of(remoteService()));
               io.reshapr.client.model.ConfigurationPlan created = new io.reshapr.client.model.ConfigurationPlan();
               created.setId(REMOTE_PLAN_ID);
               when(mock.createConfigPlan(any(io.reshapr.client.model.ConfigurationPlan.class)))
                     .thenReturn(created);
            })) {

         reconciler.doReconcile(cp, null, apiClient);

         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi, never()).createSecret(any());
         verify(mockedApi, never()).renewConfigPlanApiKey(any());
         verify(mockedApi).createConfigPlan(planCaptor.capture());

         io.reshapr.client.model.ConfigurationPlan sentPlan = planCaptor.getValue();
         assertEquals("https://pastries.prod.acme.com", sentPlan.getBackendEndpoint());
         assertNull(sentPlan.getBackendSecretId());
         assertNull(sentPlan.getCachePolicy());
         assertNull(sentPlan.getHeaderPolicy());
         assertTrue(sentPlan.getIncludedOperations() == null || sentPlan.getIncludedOperations().isEmpty());
         assertTrue(sentPlan.getExcludedOperations() == null || sentPlan.getExcludedOperations().isEmpty());
      }
   }
}
