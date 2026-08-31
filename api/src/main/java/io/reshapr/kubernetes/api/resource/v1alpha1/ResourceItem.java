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
package io.reshapr.kubernetes.api.resource.v1alpha1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.sundr.builder.annotations.Buildable;

import java.util.List;

/**
 * One resource definition, keyed by URI in {@link ResourceSpec#getResources()}.
 * Models {@code definitions/resourceItem} from {@code Resources-v1alpha1-schema.json}.
 * Content is provided via exactly one of: {@code text}, {@code blob}, or {@code remoteContent}.
 *
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "title", "description", "mimeType", "size", "text", "blob", "remoteContent", "icons", "annotations" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ResourceItem {

    @JsonPropertyDescription("The name of this resource.")
    private String name;

    @JsonPropertyDescription("Human readable title of this resource.")
    private String title;

    @JsonPropertyDescription("Human readable long description of this resource.")
    private String description;

    @JsonPropertyDescription("The MIME type of this resource.")
    private String mimeType;

    @JsonPropertyDescription("The size of this resource in bytes.")
    private Integer size;

    @JsonPropertyDescription("The text content of this resource, if applicable. Mutually exclusive with blob and remoteContent.")
    private String text;

    @JsonPropertyDescription("The base64 encoded binary content of this resource, if applicable. Mutually exclusive with text and remoteContent.")
    private String blob;

    @JsonPropertyDescription("The remote URI where this resource content can be found, if applicable. Mutually exclusive with text and blob.")
    private String remoteContent;

    @JsonPropertyDescription("Optional icons for this resource.")
    private List<ResourceIconItem> icons;

    @JsonPropertyDescription("Optional annotations for this resource.")
    private ResourceAnnotations annotations;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getBlob() { return blob; }
    public void setBlob(String blob) { this.blob = blob; }

    public String getRemoteContent() { return remoteContent; }
    public void setRemoteContent(String remoteContent) { this.remoteContent = remoteContent; }

    public List<ResourceIconItem> getIcons() { return icons; }
    public void setIcons(List<ResourceIconItem> icons) { this.icons = icons; }

    public ResourceAnnotations getAnnotations() { return annotations; }
    public void setAnnotations(ResourceAnnotations annotations) { this.annotations = annotations; }
}
