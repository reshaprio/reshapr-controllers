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
package io.reshapr.kubernetes.api.tools.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

/**
 * A reference to a tool a custom script is allowed to call. Used both as a security
 * allow-list and to pre-check elicitation secrets before running the script.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "tool", "service" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CustomToolReference {

    @JsonPropertyDescription("The name of the target tool to call.")
    private String tool;

    @JsonPropertyDescription("The target service for cross-service calls, expressed as the readable couple "
          + "'<service_name:service_version>'. Omit for a tool of the same service.")
    private String service;

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }
}

