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
import io.reshapr.client.model.ArtifactType;
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
 * {@code POST /v1/artifacts/attach} (attach an Artifact to a remote Service).
 * <p>
 * The generated {@link io.reshapr.client.api.DefaultApi#attachArtifact} fails to
 * correctly expose request parameters due to a missing body schema in the OpenAPI
 * spec. This small client fills that gap while reusing the already-authenticated
 * {@link ApiClient}.
 * @author laurent
 */
@Singleton
public class ArtifactAttachClient {

   private static final Logger logger = Logger.getLogger(ArtifactAttachClient.class);

   private static final String ATTACH_ARTIFACT_PATH = "/v1/artifacts/attach";
   private static final String BOUNDARY = "---Boundary" + java.util.UUID.randomUUID().toString();
   private static final String MULTIPART_CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

   public Artifact attachArtifact(ApiClient apiClient, String serviceId, String name, ArtifactType type, String content, boolean mainArtifact) throws ApiException {
      if (serviceId == null || type == null || content == null) {
         throw new ApiException(400, "Missing required parameters when attaching an artifact");
      }

      String multipartBody = buildMultipartBody(serviceId, name, type, content, mainArtifact);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(apiClient.getBaseUri() + ATTACH_ARTIFACT_PATH))
            .header("Content-Type", MULTIPART_CONTENT_TYPE)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(multipartBody, UTF_8));

      if (apiClient.getReadTimeout() != null) {
         requestBuilder.timeout(apiClient.getReadTimeout());
      }
      if (apiClient.getRequestInterceptor() != null) {
         apiClient.getRequestInterceptor().accept(requestBuilder);
      }

      try {
         HttpResponse<InputStream> response = apiClient.getHttpClient()
               .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

         int statusCode = response.statusCode();
         if (statusCode / 100 != 2) {
            throw new ApiException(statusCode,
                  "attachArtifact failed with status " + statusCode + ": " + readBody(response));
         }

         String body = readBody(response);
         if (body.isBlank()) {
            return null;
         }
         return apiClient.getObjectMapper().readValue(body, Artifact.class);
      } catch (IOException e) {
         throw new ApiException(e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ApiException(e);
      }
   }

   private String buildMultipartBody(String serviceId, String name, ArtifactType type, String content, boolean mainArtifact) {
      StringBuilder builder = new StringBuilder();

      addFormField(builder, "serviceId", serviceId);
      if (name != null) {
         addFormField(builder, "name", name);
      }
      addFormField(builder, "type", type.getValue());
      addFormField(builder, "mainArtifact", String.valueOf(mainArtifact));

      builder.append("--").append(BOUNDARY).append("\r\n");
      builder.append("Content-Disposition: form-data; name=\"file\"; filename=\"artifact.json\"\r\n");
      builder.append("Content-Type: application/json\r\n\r\n");
      builder.append(content).append("\r\n");

      builder.append("--").append(BOUNDARY).append("--\r\n");
      return builder.toString();
   }

   private void addFormField(StringBuilder builder, String name, String value) {
      builder.append("--").append(BOUNDARY).append("\r\n");
      builder.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
      builder.append(value).append("\r\n");
   }

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
