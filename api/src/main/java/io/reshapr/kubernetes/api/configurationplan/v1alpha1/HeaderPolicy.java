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
package io.reshapr.kubernetes.api.configurationplan.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "request", "response" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class HeaderPolicy {

   @JsonPropertyDescription("Allow/deny/rename directives applied to headers forwarded to the backend.")
   private HeaderRules request;

   @JsonPropertyDescription("Allow/deny/rename directives applied to headers returned from the backend.")
   private HeaderRules response;

   public HeaderRules getRequest() {
      return request;
   }

   public void setRequest(HeaderRules request) {
      this.request = request;
   }

   public HeaderRules getResponse() {
      return response;
   }

   public void setResponse(HeaderRules response) {
      this.response = response;
   }
}
