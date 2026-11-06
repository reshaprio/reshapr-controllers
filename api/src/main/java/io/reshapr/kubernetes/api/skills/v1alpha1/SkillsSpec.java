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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import io.fabric8.generator.annotation.ValidationRule;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.sundr.builder.annotations.Buildable;

import java.util.Map;

/**
 * This the {@code specification} of a {@link Skills} custom resource. Validation constraints mirror the
 * control plane {@code Skills-v1alpha1} schema so that invalid resources are rejected by the Kubernetes
 * API server before reaching the control plane.
 * @author vaishnav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "service", "skills", "keepOnDelete" })
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class SkillsSpec {

    @JsonPropertyDescription("Holds reference information about the Service these skills relate to.")
    @Required
    private ServiceRef service;

    @JsonPropertyDescription("The skills are defined with the skill name as key. The name must match the 'name' of the SKILL.md frontmatter.")
    @Required
    @Size(min = 1)
    @ValidationRule(value = "self.all(k, k.matches('^[a-z0-9]+(-[a-z0-9]+)*$'))",
          message = "skill names must be lowercase alphanumeric words separated by single hyphens")
    private Map<String, SkillItem> skills;

    @JsonPropertyDescription("Flag to keep artifact in control plane when deleting this resource. Default is false.")
    private boolean keepOnDelete = false;

    public ServiceRef getService() {
        return service;
    }

    public void setService(ServiceRef service) {
        this.service = service;
    }

    public Map<String, SkillItem> getSkills() {
        return skills;
    }

    public void setSkills(Map<String, SkillItem> skills) {
        this.skills = skills;
    }

    public boolean isKeepOnDelete() {
        return keepOnDelete;
    }

    public void setKeepOnDelete(boolean keepOnDelete) {
        this.keepOnDelete = keepOnDelete;
    }
}

