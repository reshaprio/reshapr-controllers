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
import io.sundr.builder.annotations.Buildable;

import java.util.List;

/**
 * Icon entry for a {@link ResourceItem} or {@link ResourceTemplateItem}.
 * Models {@code definitions/iconItem} from {@code Resources-v1alpha1-schema.json}.
 *
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ResourceIconItem {

    @JsonPropertyDescription("The source URI of this icon.")
    private String src;

    @JsonPropertyDescription("The MIME type of this icon.")
    private String mimeType;

    @JsonPropertyDescription("Array of icon sizes (e.g. '32x32', '64x64').")
    private List<String> sizes;

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public List<String> getSizes() { return sizes; }
    public void setSizes(List<String> sizes) { this.sizes = sizes; }
}
