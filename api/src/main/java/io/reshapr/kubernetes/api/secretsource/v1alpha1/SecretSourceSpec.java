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
package io.reshapr.kubernetes.api.secretsource.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

import java.util.List;

/**
 * This the {@code specification} of a {@link SecretSource} custom resource.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "secrets", "keepOnDelete" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class SecretSourceSpec {

   @JsonPropertyDescription("The list of secrets to synchronize with the reShapr control plane")
   private List<SecretSpec> secrets;

   @JsonPropertyDescription("Flag to keep control plane Secrets when deleting this resource. Default is false")
   private boolean keepOnDelete = false;

   public List<SecretSpec> getSecrets() {
      return secrets;
   }

   public void setSecrets(List<SecretSpec> secrets) {
      this.secrets = secrets;
   }

   public boolean isKeepOnDelete() {
      return keepOnDelete;
   }

   public void setKeepOnDelete(boolean keepOnDelete) {
      this.keepOnDelete = keepOnDelete;
   }
}
