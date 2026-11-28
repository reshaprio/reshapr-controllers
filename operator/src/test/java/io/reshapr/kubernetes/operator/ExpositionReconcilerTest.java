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
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.ConfigurationPlan;
import io.reshapr.client.model.ExpositionReference;
import io.reshapr.client.model.GatewayGroup;
import io.reshapr.kubernetes.api.exposition.v1alpha1.Exposition;
import io.reshapr.kubernetes.api.exposition.v1alpha1.ExpositionSpec;
import io.reshapr.kubernetes.api.exposition.v1alpha1.ExpositionStatus;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpositionReconciler}.
 * Covers creation, spec update (delete-before-recreate ordering), no-op updates,
 * {@code keepOnDelete} handling, 404/409 recovery and adoption, dependency resolution,
 * and finalizer cleanup.
 *
 * @author vaishnav
 */
class ExpositionReconcilerTest {

   private static final String INSTANCE = "reshapr-control-plane-ctrl.reshapr-system";
   private static final String ORG = "reshapr";
   private static final String EXPOSITION_NAME = "pastries-prod-exposition";
   private static final String REMOTE_SERVICE_ID = "svc-1234";
   private static final String REMOTE_PLAN_ID = "plan-5678";
   private static final String REMOTE_PLAN_V2_ID = "plan-9999";
   private static final String REMOTE_GROUP_ID = "grp-4321";
   private static final String OLD_EXPOSITION_ID = "exp-old";
   private static final String NEW_EXPOSITION_ID = "exp-new";

   private ReshaprApiClientFactory factory;
   private ApiClient apiClient;
   private ExpositionReconciler reconciler;

   @BeforeEach
   void setUp() {
      factory = mock(ReshaprApiClientFactory.class);
      apiClient = mock(ApiClient.class);
      reconciler = new ExpositionReconciler(factory);
   }

   // ── helpers ──────────────────────────────────────────────────────────────

   private Exposition buildExposition(String planName, String groupName, boolean keepOnDelete, Long generation) {
      ServiceRef ref = new ServiceRef();
      ref.setName("API Pastries");
      ref.setVersion("0.0.1");

      ExpositionSpec spec = new ExpositionSpec();
      spec.setService(ref);
      spec.setConfigurationPlan(planName);
      spec.setGatewayGroup(groupName);
      spec.setKeepOnDelete(keepOnDelete);

      Map<String, String> annotations = new HashMap<>();
      annotations.put(ReshaprAnnotations.INSTANCE, INSTANCE);
      annotations.put(ReshaprAnnotations.ORGANIZATION, ORG);

      ObjectMeta meta = new ObjectMeta();
      meta.setName(EXPOSITION_NAME);
      meta.setNamespace("default");
      meta.setGeneration(generation);
      meta.setAnnotations(annotations);

      Exposition exposition = new Exposition();
      exposition.setMetadata(meta);
      exposition.setSpec(spec);
      return exposition;
   }

   private Exposition withReadyStatus(Exposition exposition, String expositionId, long observedGeneration) {
      ExpositionStatus status = new ExpositionStatus();
      status.setExpositionId(expositionId);
      status.setObservedGeneration(observedGeneration);
      status.setStatus(Status.READY);
      exposition.setStatus(status);
      return exposition;
   }

   private io.reshapr.client.model.Service remoteService() {
      io.reshapr.client.model.Service svc = new io.reshapr.client.model.Service();
      svc.setId(REMOTE_SERVICE_ID);
      svc.setName("API Pastries");
      svc.setVersion("0.0.1");
      return svc;
   }

   private ConfigurationPlan remotePlan(String id, String name) {
      ConfigurationPlan plan = new ConfigurationPlan();
      plan.setId(id);
      plan.setName(name);
      return plan;
   }

   private GatewayGroup remoteGroup(String id, String name) {
      GatewayGroup group = new GatewayGroup();
      group.setId(id);
      group.setName(name);
      return group;
   }

   private io.reshapr.client.model.Exposition remoteExposition(String id, String name, String planId, String groupId) {
      io.reshapr.client.model.Exposition exp = new io.reshapr.client.model.Exposition();
      exp.setId(id);
      exp.setOrganizationId(ORG);
      exp.setName(name);
      exp.setConfigurationPlan(remotePlan(planId, "plan"));
      exp.setGatewayGroup(remoteGroup(groupId, "group"));
      return exp;
   }

   private void stubDependencies(DefaultApi mock, String planId, String planName) throws Exception {
      when(mock.getServices(any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn(List.of(remoteService()));
      when(mock.getConfigPlans(REMOTE_SERVICE_ID))
            .thenReturn(List.of(remotePlan(planId, planName)));
      when(mock.listGatewayGroups())
            .thenReturn(List.of(remoteGroup(REMOTE_GROUP_ID, "prod-group")));
   }

   // ── create & no-op tests ─────────────────────────────────────────────────

   @Test
   void doReconcile_firstCreation_createsExpositionAndRecordsStatus() throws Exception {
      Exposition exposition = buildExposition("prod-plan", "prod-group", false, 1L);
      ArgumentCaptor<ExpositionReference> refCaptor = ArgumentCaptor.forClass(ExpositionReference.class);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_ID, "prod-plan");
         when(mock.createExposition(any(ExpositionReference.class)))
               .thenReturn(remoteExposition(NEW_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api, never()).deleteExposition(any());
         verify(api).createExposition(refCaptor.capture());

         ExpositionReference sent = refCaptor.getValue();
         assertEquals(ORG, sent.getOrganizationId());
         assertEquals(EXPOSITION_NAME, sent.getName());
         assertEquals(REMOTE_PLAN_ID, sent.getConfigurationPlanId());
         assertEquals(REMOTE_GROUP_ID, sent.getGatewayGroupId());

         assertNotNull(exposition.getStatus());
         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertNull(exposition.getStatus().getMessage());
         assertEquals(NEW_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertEquals(1L, exposition.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenAlreadyCreatedAndSpecUnchanged_skipsReconciliation() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", false, 200L), OLD_EXPOSITION_ID, 200L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         assertTrue(result.isNoUpdate());
         verify(mocked.constructed().get(0), never()).createExposition(any());
         verify(mocked.constructed().get(0), never()).deleteExposition(any());
      }
   }

   // ── spec update tests ────────────────────────────────────────────────────

   @Test
   void doReconcile_whenSpecUpdated_deletesPreviousBeforeCreatingReplacement() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
         when(mock.createExposition(any(ExpositionReference.class)))
               .thenReturn(remoteExposition(NEW_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_V2_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         InOrder order = inOrder(api);
         order.verify(api).getExposition(OLD_EXPOSITION_ID);
         order.verify(api).deleteExposition(OLD_EXPOSITION_ID);
         order.verify(api).createExposition(any(ExpositionReference.class));

         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertNull(exposition.getStatus().getMessage());
         assertEquals(NEW_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertEquals(2L, exposition.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenOnlyKeepOnDeleteChanged_skipsDeleteAndRecreate() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", true, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_ID, "prod-plan");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api, never()).deleteExposition(any());
         verify(api, never()).createExposition(any());

         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertEquals(OLD_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertEquals(2L, exposition.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenSpecUpdatedWithKeepOnDeleteTrue_preservesPreviousAndRecordsError() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", true, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api, never()).deleteExposition(any());
         verify(api, never()).createExposition(any());

         assertEquals(Status.ERROR, exposition.getStatus().getStatus());
         assertTrue(exposition.getStatus().getMessage().contains("keepOnDelete"));
         assertEquals(OLD_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }

   @Test
   void doReconcile_whenSpecUpdatedAndPreviousReturns404OnGet_recreatesWithoutDeleting() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID)).thenThrow(new ApiException(404, "Not Found"));
         when(mock.createExposition(any(ExpositionReference.class)))
               .thenReturn(remoteExposition(NEW_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_V2_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api, never()).deleteExposition(any());
         verify(api).createExposition(any(ExpositionReference.class));

         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertEquals(NEW_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertEquals(2L, exposition.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenSpecUpdatedAndDeleteReturns404_proceedsToCreate() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
         doThrow(new ApiException(404, "Not Found")).when(mock).deleteExposition(OLD_EXPOSITION_ID);
         when(mock.createExposition(any(ExpositionReference.class)))
               .thenReturn(remoteExposition(NEW_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_V2_ID, REMOTE_GROUP_ID));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api).deleteExposition(OLD_EXPOSITION_ID);
         verify(api).createExposition(any(ExpositionReference.class));

         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertEquals(NEW_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenSpecUpdatedAndDeleteFails_doesNotCreateAndPreservesExpositionId() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
         doThrow(new ApiException(500, "Internal Server Error")).when(mock).deleteExposition(OLD_EXPOSITION_ID);
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api).deleteExposition(OLD_EXPOSITION_ID);
         verify(api, never()).createExposition(any());

         assertEquals(Status.ERROR, exposition.getStatus().getStatus());
         assertTrue(exposition.getStatus().getMessage().contains("500"));
         assertEquals(OLD_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }

   @Test
   void doReconcile_whenCreateFailsAfterDelete_clearsStaleExpositionId() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan-v2", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_V2_ID, "prod-plan-v2");
         when(mock.getExposition(OLD_EXPOSITION_ID))
               .thenReturn(remoteExposition(OLD_EXPOSITION_ID, EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID));
         when(mock.createExposition(any(ExpositionReference.class)))
               .thenThrow(new ApiException(503, "Service Unavailable"));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api).deleteExposition(OLD_EXPOSITION_ID);
         verify(api).createExposition(any(ExpositionReference.class));

         assertEquals(Status.ERROR, exposition.getStatus().getStatus());
         assertNull(exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }

   // ── 409 conflict & adoption tests ────────────────────────────────────────

   @Test
   void doReconcile_whenCreateReturns409AndMatchingRemoteExists_adoptsExposition() throws Exception {
      Exposition exposition = buildExposition("prod-plan", "prod-group", false, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_ID, "prod-plan");
         when(mock.createExposition(any(ExpositionReference.class))).thenThrow(new ApiException(409, "Conflict"));
         when(mock.getExpositions(REMOTE_SERVICE_ID, REMOTE_GROUP_ID))
               .thenReturn(List.of(remoteExposition("exp-adopted", EXPOSITION_NAME, REMOTE_PLAN_ID, REMOTE_GROUP_ID)));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api).createExposition(any(ExpositionReference.class));
         verify(api).getExpositions(REMOTE_SERVICE_ID, REMOTE_GROUP_ID);

         assertEquals(Status.READY, exposition.getStatus().getStatus());
         assertNull(exposition.getStatus().getMessage());
         assertEquals("exp-adopted", exposition.getStatus().getExpositionId());
         assertEquals(1L, exposition.getStatus().getObservedGeneration());
         assertTrue(result.isPatchStatus());
      }
   }

   @Test
   void doReconcile_whenCreateReturns409AndRemoteTargetDiffers_recordsErrorAndReschedules() throws Exception {
      Exposition exposition = buildExposition("prod-plan", "prod-group", false, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         stubDependencies(mock, REMOTE_PLAN_ID, "prod-plan");
         when(mock.createExposition(any(ExpositionReference.class))).thenThrow(new ApiException(409, "Conflict"));
         when(mock.getExpositions(REMOTE_SERVICE_ID, REMOTE_GROUP_ID))
               .thenReturn(List.of(remoteExposition("exp-other", EXPOSITION_NAME, "different-plan-id", REMOTE_GROUP_ID)));
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         assertEquals(Status.ERROR, exposition.getStatus().getStatus());
         assertTrue(exposition.getStatus().getMessage().contains("409"));
         assertNull(exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }

   // ── validation & missing dependency tests ────────────────────────────────

   @Test
   void doReconcile_whenDependencyMissingDuringUpdate_doesNotDeletePreviousExposition() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("missing-plan", "prod-group", false, 2L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class, (mock, ctx) -> {
         when(mock.getServices(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(List.of(remoteService()));
         when(mock.getConfigPlans(REMOTE_SERVICE_ID)).thenReturn(List.of());
      })) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         DefaultApi api = mocked.constructed().get(0);
         verify(api, never()).deleteExposition(any());
         verify(api, never()).createExposition(any());

         assertEquals(Status.IN_PROGRESS, exposition.getStatus().getStatus());
         assertEquals(OLD_EXPOSITION_ID, exposition.getStatus().getExpositionId());
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }

   @Test
   void doReconcile_whenSpecInvalid_recordsErrorWithoutMutating() throws Exception {
      Exposition exposition = buildExposition("", "prod-group", false, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         UpdateControl<Exposition> result = reconciler.doReconcile(exposition, null, apiClient);

         assertEquals(Status.ERROR, exposition.getStatus().getStatus());
         assertTrue(exposition.getStatus().getMessage().contains("configurationPlan"));
         assertTrue(result.isPatchStatus());
         assertTrue(result.getScheduleDelay().isEmpty());
         verify(mocked.constructed().get(0), never()).createExposition(any());
      }
   }

   // ── cleanup tests ────────────────────────────────────────────────────────

   @Test
   void cleanup_deletesControlPlaneExposition() throws Exception {
      when(factory.createAuthenticatedApiClient(INSTANCE, ORG)).thenReturn(apiClient);
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", false, 1L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl result = reconciler.cleanup(exposition, null);

         verify(mocked.constructed().get(0)).deleteExposition(OLD_EXPOSITION_ID);
         assertTrue(result.isRemoveFinalizer());
      }
   }

   @Test
   void cleanup_whenKeepOnDeleteTrue_skipsDeletion() throws Exception {
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", true, 1L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class)) {
         DeleteControl result = reconciler.cleanup(exposition, null);

         assertEquals(0, mocked.constructed().size());
         verify(factory, never()).createAuthenticatedApiClient(any(), any());
         assertTrue(result.isRemoveFinalizer());
      }
   }

   @Test
   void cleanup_whenDeleteReturns404_removesFinalizer() throws Exception {
      when(factory.createAuthenticatedApiClient(INSTANCE, ORG)).thenReturn(apiClient);
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", false, 1L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> doThrow(new ApiException(404, "Not Found")).when(mock).deleteExposition(OLD_EXPOSITION_ID))) {
         DeleteControl result = reconciler.cleanup(exposition, null);

         verify(mocked.constructed().get(0)).deleteExposition(OLD_EXPOSITION_ID);
         assertTrue(result.isRemoveFinalizer());
      }
   }

   @Test
   void cleanup_whenDeleteFails_keepsFinalizerAndReschedules() throws Exception {
      when(factory.createAuthenticatedApiClient(INSTANCE, ORG)).thenReturn(apiClient);
      Exposition exposition = withReadyStatus(
            buildExposition("prod-plan", "prod-group", false, 1L), OLD_EXPOSITION_ID, 1L);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> doThrow(new ApiException(500, "Internal Server Error")).when(mock).deleteExposition(OLD_EXPOSITION_ID))) {
         DeleteControl result = reconciler.cleanup(exposition, null);

         verify(mocked.constructed().get(0)).deleteExposition(OLD_EXPOSITION_ID);
         assertFalse(result.isRemoveFinalizer());
         assertTrue(result.getScheduleDelay().isPresent());
      }
   }
}
