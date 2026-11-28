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
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.Artifact;
import io.reshapr.client.model.ArtifactReference;
import io.reshapr.client.model.ArtifactType;
import io.reshapr.client.model.ServiceType;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.service.v1alpha1.Service;
import io.reshapr.kubernetes.api.service.v1alpha1.ServiceSpec;
import io.reshapr.kubernetes.api.service.v1alpha1.ServiceStatus;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.client.ArtifactImportClient;
import io.reshapr.kubernetes.operator.client.ArtifactImportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ServiceReconciler} and generated OpenAPI client AsyncAPI model support.
 */
class ServiceReconcilerTest {

   private static final String INSTANCE = "reshapr-control-plane-ctrl.reshapr-system";
   private static final String ORG = "reshapr";
   private static final String ASYNCAPI_URL =
         "https://raw.githubusercontent.com/reshaprio/reshapr/refs/heads/main/dev/mcp-events/orders-asyncapi.yaml";

   private Service buildAsyncApiService() {
      ServiceSpec spec = new ServiceSpec();
      spec.setName("Orders Events API");
      spec.setVersion("1.0.0");
      spec.setUrl(ASYNCAPI_URL);

      Map<String, String> annotations = new HashMap<>();
      annotations.put(ReshaprAnnotations.INSTANCE, INSTANCE);
      annotations.put(ReshaprAnnotations.ORGANIZATION, ORG);

      ObjectMeta meta = new ObjectMeta();
      meta.setName("orders-events-api");
      meta.setNamespace("default");
      meta.setGeneration(1L);
      meta.setAnnotations(annotations);

      Service service = new Service();
      service.setMetadata(meta);
      service.setSpec(spec);
      return service;
   }

   private io.reshapr.client.model.Service remoteAsyncService(String id) {
      io.reshapr.client.model.Service svc = new io.reshapr.client.model.Service();
      svc.setId(id);
      svc.setOrganizationId(ORG);
      svc.setName("Orders Events API");
      svc.setVersion("1.0.0");
      svc.setType(ServiceType.ASYNC);
      return svc;
   }

   @Test
   void generatedClientDeserializesAsyncServiceAndAsyncApiSpecArtifact() throws Exception {
      ApiClient apiClient = new ApiClient();

      String serviceJson = """
            {
              "id": "svc-async-1",
              "organizationId": "reshapr",
              "name": "Orders Events API",
              "version": "1.0.0",
              "type": "ASYNC"
            }
            """;
      io.reshapr.client.model.Service service =
            apiClient.getObjectMapper().readValue(serviceJson, io.reshapr.client.model.Service.class);
      assertEquals("svc-async-1", service.getId());
      assertEquals(ServiceType.ASYNC, service.getType());

      String artifactJson = """
            {
              "id": "art-async-1",
              "organizationId": "reshapr",
              "name": "orders-asyncapi.yaml",
              "mainArtifact": true,
              "type": "ASYNC_API_SPEC",
              "sourceArtifact": "orders-asyncapi.yaml"
            }
            """;
      Artifact artifact = apiClient.getObjectMapper().readValue(artifactJson, Artifact.class);
      assertEquals(ArtifactType.ASYNC_API_SPEC, artifact.getType());

      ArtifactReference ref = apiClient.getObjectMapper().readValue(artifactJson, ArtifactReference.class);
      assertEquals(ArtifactType.ASYNC_API_SPEC, ref.getType());
   }

   @Test
   void doReconcile_importsAsyncApiServiceAndUpdatesStatus() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ArtifactImportClient importClient = mock(ArtifactImportClient.class);
      ServiceReconciler reconciler = new ServiceReconciler(factory, importClient);
      ApiClient apiClient = mock(ApiClient.class);
      Service cr = buildAsyncApiService();

      when(importClient.importArtifactFromUrl(eq(apiClient), any(ArtifactImportRequest.class)))
            .thenReturn(remoteAsyncService("svc-async-1"));

      ArgumentCaptor<ArtifactImportRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactImportRequest.class);

      UpdateControl<Service> result = reconciler.doReconcile(cr, null, apiClient);

      verify(importClient).importArtifactFromUrl(eq(apiClient), requestCaptor.capture());
      ArtifactImportRequest sent = requestCaptor.getValue();
      assertEquals(ASYNCAPI_URL, sent.url());
      assertEquals(Boolean.TRUE, sent.mainArtifact());
      assertEquals("Orders Events API", sent.serviceName());
      assertEquals("1.0.0", sent.serviceVersion());

      assertTrue(result.isPatchStatus());
      assertNotNull(cr.getStatus());
      assertEquals(Status.READY, cr.getStatus().getStatus());
      assertEquals("svc-async-1", cr.getStatus().getServiceId());
      assertEquals(1L, cr.getStatus().getObservedGeneration());
      assertNull(cr.getStatus().getMessage());
   }

   @Test
   void doReconcile_whenAlreadyImportedAndSpecUnchanged_skipsReimport() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ArtifactImportClient importClient = mock(ArtifactImportClient.class);
      ServiceReconciler reconciler = new ServiceReconciler(factory, importClient);
      ApiClient apiClient = mock(ApiClient.class);
      Service cr = buildAsyncApiService();

      ServiceStatus status = new ServiceStatus();
      status.setServiceId("svc-async-1");
      status.setObservedGeneration(1L);
      status.setStatus(Status.READY);
      cr.setStatus(status);

      UpdateControl<Service> result = reconciler.doReconcile(cr, null, apiClient);

      assertTrue(result.isNoUpdate());
      verify(importClient, never()).importArtifactFromUrl(any(), any());
   }

   @Test
   void doReconcile_withoutUrl_recordsErrorStatus() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ArtifactImportClient importClient = mock(ArtifactImportClient.class);
      ServiceReconciler reconciler = new ServiceReconciler(factory, importClient);
      ApiClient apiClient = mock(ApiClient.class);
      Service cr = buildAsyncApiService();
      cr.getSpec().setUrl("  ");

      UpdateControl<Service> result = reconciler.doReconcile(cr, null, apiClient);

      assertTrue(result.isPatchStatus());
      assertEquals(Status.ERROR, cr.getStatus().getStatus());
      assertTrue(cr.getStatus().getMessage().contains("spec.url"));
      verify(importClient, never()).importArtifactFromUrl(any(), any());
   }

   @Test
   void cleanup_resolvesAsyncServiceAndDeletesIt() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ArtifactImportClient importClient = mock(ArtifactImportClient.class);
      ServiceReconciler reconciler = new ServiceReconciler(factory, importClient);
      ApiClient apiClient = mock(ApiClient.class);
      Service cr = buildAsyncApiService();

      when(factory.createAuthenticatedApiClient(INSTANCE, ORG)).thenReturn(apiClient);

      try (MockedConstruction<DefaultApi> mocked = Mockito.mockConstruction(DefaultApi.class,
            (mock, ctx) -> when(mock.getServices(any(BigDecimal.class), any(BigDecimal.class)))
                  .thenReturn(List.of(remoteAsyncService("svc-async-1"))))) {

         DeleteControl result = reconciler.cleanup(cr, null);

         DefaultApi mockedApi = mocked.constructed().get(0);
         verify(mockedApi).deleteService("svc-async-1");
         assertTrue(result.isRemoveFinalizer());
      }
   }

   @Test
   void cleanup_whenKeepOnDeleteIsTrue_leavesRemoteServiceInPlace() throws Exception {
      ReshaprApiClientFactory factory = mock(ReshaprApiClientFactory.class);
      ArtifactImportClient importClient = mock(ArtifactImportClient.class);
      ServiceReconciler reconciler = new ServiceReconciler(factory, importClient);
      Service cr = buildAsyncApiService();
      cr.getSpec().setKeepOnDelete(true);

      DeleteControl result = reconciler.cleanup(cr, null);

      assertTrue(result.isRemoveFinalizer());
      assertFalse(result.getScheduleDelay().isPresent());
      verify(factory, never()).createAuthenticatedApiClient(any(), any());
   }
}

