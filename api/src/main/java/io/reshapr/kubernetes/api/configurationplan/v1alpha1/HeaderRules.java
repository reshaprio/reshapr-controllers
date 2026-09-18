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

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "allow", "deny", "rename" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class HeaderRules {

   @JsonPropertyDescription("Header names explicitly allowed to be propagated. When provided, only these headers are forwarded (minus any restricted by default).")
   private List<String> allow;

   @JsonPropertyDescription("Header names explicitly denied from being propagated. Takes precedence over the allow-list.")
   private List<String> deny;

   @JsonPropertyDescription("Rename directives moving an incoming header to a different name before propagation.")
   private List<HeaderRename> rename;

   public List<String> getAllow() {
      return allow;
   }

   public void setAllow(List<String> allow) {
      this.allow = allow;
   }

   public List<String> getDeny() {
      return deny;
   }

   public void setDeny(List<String> deny) {
      this.deny = deny;
   }

   public List<HeaderRename> getRename() {
      return rename;
   }

   public void setRename(List<HeaderRename> rename) {
      this.rename = rename;
   }
}
