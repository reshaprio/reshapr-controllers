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
package io.reshapr.kubernetes.api.skills.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;

import java.util.List;
import java.util.Map;

/**
 * A single skill definition. A skill is a directory (holding a {@code SKILL.md} and optional
 * supporting files) hosted on an HTTP(S) server that the gateway fetches and serves over MCP.
 * For example, with {@code remoteContent: https://host/my-skill} and {@code files: [references/A.md]},
 * the gateway fetches {@code https://host/my-skill/SKILL.md} and {@code https://host/my-skill/references/A.md}.
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "remoteContent", "path", "files", "_meta" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class SkillItem {

    @JsonPropertyDescription("The remote HTTP(S) URL of the skill directory (the one holding SKILL.md).")
    @Required
    @Pattern("^https?://")
    private String remoteContent;

    @JsonPropertyDescription("Optional organizational prefix of the skill URI, without leading or trailing slash (e.g. 'acme/billing').")
    @Pattern("^[^/\\s]+(/[^/\\s]+)*$")
    private String path;

    @JsonPropertyDescription("Supporting files of the skill, as paths relative to the skill directory (SKILL.md is implicit).")
    private List<String> files;

    @JsonProperty("_meta")
    @JsonPropertyDescription("Optional metadata attached to the SKILL.md resource.")
    @PreserveUnknownFields
    @SchemaFrom(type = FreeFormObject.class)
    private Map<String, Object> meta;

    public String getRemoteContent() {
        return remoteContent;
    }

    public void setRemoteContent(String remoteContent) {
        this.remoteContent = remoteContent;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    /**
     * Schema placeholder so that {@code _meta} is rendered as a free-form object in the CRD
     * (accepting any value type) instead of a map of objects.
     */
    private static final class FreeFormObject {
    }
}
