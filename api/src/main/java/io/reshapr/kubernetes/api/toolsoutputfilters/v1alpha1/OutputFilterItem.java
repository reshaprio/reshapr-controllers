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
package io.reshapr.kubernetes.api.toolsoutputfilters.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

import java.util.List;

/**
 * A single tool output filter definition.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "jsonRetain", "jsonPatches", "compact", "convertToToon" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class OutputFilterItem {

    @JsonPropertyDescription("JSON Pointer paths to retain in the output. Only these branches are kept, everything else is removed.")
    private List<String> jsonRetain;

    @JsonPropertyDescription("JSON Patch operations (RFC 6902) to apply on the output.")
    private List<JsonPatchOperation> jsonPatches;

    @JsonPropertyDescription("Whether to compact the JSON output by removing null values, empty strings, empty arrays and empty objects.")
    private Boolean compact;

    @JsonPropertyDescription("Whether to convert the final JSON output to Toon format for a more compact LLM-friendly representation.")
    private Boolean convertToToon;

    public List<String> getJsonRetain() {
        return jsonRetain;
    }

    public void setJsonRetain(List<String> jsonRetain) {
        this.jsonRetain = jsonRetain;
    }

    public List<JsonPatchOperation> getJsonPatches() {
        return jsonPatches;
    }

    public void setJsonPatches(List<JsonPatchOperation> jsonPatches) {
        this.jsonPatches = jsonPatches;
    }

    public Boolean getCompact() {
        return compact;
    }

    public void setCompact(Boolean compact) {
        this.compact = compact;
    }

    public Boolean getConvertToToon() {
        return convertToToon;
    }

    public void setConvertToToon(Boolean convertToToon) {
        this.convertToToon = convertToToon;
    }
}
