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

import java.util.List;
import java.util.Map;

/**
 * One custom tool definition. It is either a declarative mapping to a target tool
 * ({@code tool} + {@code arguments}) or a custom script logic ({@code script} + {@code tools}).
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "tool", "script", "tools", "description", "input", "arguments" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CustomToolItem {

    @JsonPropertyDescription("The name of the target tool, e.g., 'user', 'createIssue', etc.")
    private String tool;

    @JsonPropertyDescription("A custom logic executed representing multi-tool orchestration")
    private String script;

    @JsonPropertyDescription("The exhaustive list of tools the script is allowed to call. Required when 'script' is set.")
    private List<CustomToolReference> tools;

    @JsonPropertyDescription("Human readable long description of this custom tool.")
    private String description;

    @JsonPropertyDescription("A JSON Schema object defining the expected parameters for the tool.")
    private CustomToolInput input;

    @JsonPropertyDescription("The target tool arguments result. Actually a template reusing ${input} notation.")
    private Map<String, Object> arguments;

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public List<CustomToolReference> getTools() {
        return tools;
    }

    public void setTools(List<CustomToolReference> tools) {
        this.tools = tools;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CustomToolInput getInput() {
        return input;
    }

    public void setInput(CustomToolInput input) {
        this.input = input;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}

