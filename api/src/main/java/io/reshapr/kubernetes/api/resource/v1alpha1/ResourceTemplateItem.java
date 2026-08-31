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
 * One resource template definition, keyed by URI in {@link ResourceSpec#getResourceTemplates()}.
 * Models {@code definitions/resourceTemplateItem} from {@code Resources-v1alpha1-schema.json}.
 *
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "title", "description", "mimeType", "icons", "annotations" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ResourceTemplateItem {

    @JsonPropertyDescription("The name of this resource template.")
    private String name;

    @JsonPropertyDescription("Human readable title of this resource template.")
    private String title;

    @JsonPropertyDescription("Human readable long description of this resource template.")
    private String description;

    @JsonPropertyDescription("The MIME type of these resource templates.")
    private String mimeType;

    @JsonPropertyDescription("Optional icons for this resource template.")
    private List<ResourceIconItem> icons;

    @JsonPropertyDescription("Optional annotations for this resource template.")
    private ResourceAnnotations annotations;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public List<ResourceIconItem> getIcons() { return icons; }
    public void setIcons(List<ResourceIconItem> icons) { this.icons = icons; }

    public ResourceAnnotations getAnnotations() { return annotations; }
    public void setAnnotations(ResourceAnnotations annotations) { this.annotations = annotations; }
}
