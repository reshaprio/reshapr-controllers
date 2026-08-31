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
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.sundr.builder.annotations.Buildable;

import java.util.Map;

/**
 * This is the {@code specification} of a {@link Resource} custom resource.
 * It holds a reference to a Service and typed maps of resource definitions
 * (and optional resource templates) that will be attached as
 * {@code RESHAPR_RESOURCES} artifacts in the control plane.
 * <p>
 * At least one of {@code resources} or {@code resourceTemplates} must be present.
 *
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "service", "resources", "resourceTemplates" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ResourceSpec {

    @JsonPropertyDescription("Holds reference information about the Service this resource artifact relates to.")
    private ServiceRef service;

    @JsonPropertyDescription("The resource definitions, keyed by URI.")
    private Map<String, ResourceItem> resources;

    @JsonPropertyDescription("The resource template definitions, keyed by URI template.")
    private Map<String, ResourceTemplateItem> resourceTemplates;

    public ServiceRef getService() {
        return service;
    }

    public void setService(ServiceRef service) {
        this.service = service;
    }

    public Map<String, ResourceItem> getResources() {
        return resources;
    }

    public void setResources(Map<String, ResourceItem> resources) {
        this.resources = resources;
    }

    public Map<String, ResourceTemplateItem> getResourceTemplates() {
        return resourceTemplates;
    }

    public void setResourceTemplates(Map<String, ResourceTemplateItem> resourceTemplates) {
        this.resourceTemplates = resourceTemplates;
    }
}
