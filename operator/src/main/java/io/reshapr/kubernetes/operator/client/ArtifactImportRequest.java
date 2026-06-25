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

import java.util.List;

/**
 * Immutable request payload for importing an Artifact from a remote URL via the
 * {@code application/x-www-form-urlencoded} variant of {@code POST /v1/artifacts}.
 * <p>
 * Mirrors the form fields accepted by the control plane: {@code url} (required),
 * {@code mainArtifact}, {@code secretName}, {@code serviceName}, {@code serviceVersion},
 * {@code includedOperations} and {@code excludedOperations}.
 *
 * @param url                The remote URL of the specification to import (required).
 * @param mainArtifact       Whether this artifact is the primary one (defaults to true on the server).
 * @param secretName         Name of the Secret to use for authenticating the remote fetch.
 * @param serviceName        Name to assign to the imported Service (overrides the specification).
 * @param serviceVersion     Version to assign to the imported Service (overrides the specification).
 * @param includedOperations List of operation names to include from the specification.
 * @param excludedOperations List of operation names to exclude from the specification.
 * @author laurent
 */
public record ArtifactImportRequest(
      String url,
      Boolean mainArtifact,
      String secretName,
      String serviceName,
      String serviceVersion,
      List<String> includedOperations,
      List<String> excludedOperations) {

   /**
    * Start building an {@link ArtifactImportRequest}.
    * @return a new {@link Builder}.
    */
   public static Builder builder() {
      return new Builder();
   }

   /**
    * Fluent builder for {@link ArtifactImportRequest}.
    */
   public static final class Builder {
      private String url;
      private Boolean mainArtifact;
      private String secretName;
      private String serviceName;
      private String serviceVersion;
      private List<String> includedOperations;
      private List<String> excludedOperations;

      public Builder url(String url) {
         this.url = url;
         return this;
      }

      public Builder mainArtifact(Boolean mainArtifact) {
         this.mainArtifact = mainArtifact;
         return this;
      }

      public Builder secretName(String secretName) {
         this.secretName = secretName;
         return this;
      }

      public Builder serviceName(String serviceName) {
         this.serviceName = serviceName;
         return this;
      }

      public Builder serviceVersion(String serviceVersion) {
         this.serviceVersion = serviceVersion;
         return this;
      }

      public Builder includedOperations(List<String> includedOperations) {
         this.includedOperations = includedOperations;
         return this;
      }

      public Builder excludedOperations(List<String> excludedOperations) {
         this.excludedOperations = excludedOperations;
         return this;
      }

      public ArtifactImportRequest build() {
         return new ArtifactImportRequest(url, mainArtifact, secretName, serviceName,
               serviceVersion, includedOperations, excludedOperations);
      }
   }
}

