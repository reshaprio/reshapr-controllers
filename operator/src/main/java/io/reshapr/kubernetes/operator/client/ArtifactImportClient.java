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
package io.reshapr.kubernetes.operator.client;

import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.model.Artifact;

import io.reshapr.client.model.Service;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Hand-written client for the {@code application/x-www-form-urlencoded} variant of
 * {@code POST /v1/artifacts} (import an Artifact from a remote URL).
 * <p>
 * The generated {@link io.reshapr.client.api.DefaultApi#importArtifact} only exposes the
 * {@code multipart/form-data} (file upload) variant — OpenAPI Generator emits a single
 * method per operation and keeps only the first declared media type. This small client
 * fills that gap while reusing the already-authenticated {@link ApiClient} (base URI,
 * JWT bearer interceptor, timeouts and Jackson object mapper).
 * @author laurent
 */
@Singleton
public class ArtifactImportClient {

   private static final Logger logger = Logger.getLogger(ArtifactImportClient.class);

   /** Path of the artifact import endpoint, relative to the control plane base URI. */
   private static final String IMPORT_ARTIFACT_PATH = "/v1/artifacts";

   private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

   /**
    * Import an Artifact from a remote URL, posting the form fields as
    * {@code application/x-www-form-urlencoded}. This discovers a Service.
    * @param apiClient An authenticated {@link ApiClient} (base URI and bearer interceptor configured).
    * @param request   The import request; {@link ArtifactImportRequest#url()} is required.
    * @return The imported {@link Artifact}, or {@code null} if the response had no body.
    * @throws ApiException if the request fails or the server returns a non-2xx status.
    */
   public Service importArtifactFromUrl(ApiClient apiClient, ArtifactImportRequest request) throws ApiException {
      if (request == null || request.url() == null || request.url().isBlank()) {
         throw new ApiException(400, "Missing required parameter 'url' when importing an artifact");
      }

      String formBody = encodeForm(request);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(apiClient.getBaseUri() + IMPORT_ARTIFACT_PATH))
            .header("Content-Type", FORM_CONTENT_TYPE)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(formBody, UTF_8));

      if (apiClient.getReadTimeout() != null) {
         requestBuilder.timeout(apiClient.getReadTimeout());
      }
      // Apply the configured request interceptor (adds the Authorization: Bearer header).
      if (apiClient.getRequestInterceptor() != null) {
         apiClient.getRequestInterceptor().accept(requestBuilder);
      }

      try {
         HttpResponse<InputStream> response = apiClient.getHttpClient()
               .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

         int statusCode = response.statusCode();
         if (statusCode / 100 != 2) {
            throw new ApiException(statusCode,
                  "importArtifact failed with status " + statusCode + ": " + readBody(response));
         }

         String body = readBody(response);
         if (body.isBlank()) {
            logger.debugf("importArtifact returned an empty body (status %d)", statusCode);
            return null;
         }
         return apiClient.getObjectMapper().readValue(body, Service.class);
      } catch (IOException e) {
         throw new ApiException(e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ApiException(e);
      }
   }

   /** Encode the request fields as an {@code application/x-www-form-urlencoded} body. */
   private String encodeForm(ArtifactImportRequest request) {
      List<String> parts = new ArrayList<>();
      parts.add(field("url", request.url()));
      if (request.mainArtifact() != null) {
         parts.add(field("mainArtifact", request.mainArtifact().toString()));
      }
      if (request.secretName() != null) {
         parts.add(field("secretName", request.secretName()));
      }
      if (request.serviceName() != null) {
         parts.add(field("serviceName", request.serviceName()));
      }
      if (request.serviceVersion() != null) {
         parts.add(field("serviceVersion", request.serviceVersion()));
      }
      if (request.includedOperations() != null) {
         for (String operation : request.includedOperations()) {
            parts.add(field("includedOperations", operation));
         }
      }
      if (request.excludedOperations() != null) {
         for (String operation : request.excludedOperations()) {
            parts.add(field("excludedOperations", operation));
         }
      }
      return String.join("&", parts);
   }

   private String field(String name, String value) {
      return ApiClient.urlEncode(name) + "=" + ApiClient.urlEncode(value);
   }

   /** Read the response body fully as a UTF-8 string, transparently handling gzip. */
   private String readBody(HttpResponse<InputStream> response) throws IOException {
      InputStream bodyStream = ApiClient.getResponseBody(response);
      if (bodyStream == null) {
         return "";
      }
      try (InputStream in = bodyStream) {
         return new String(in.readAllBytes(), UTF_8);
      }
   }
}

