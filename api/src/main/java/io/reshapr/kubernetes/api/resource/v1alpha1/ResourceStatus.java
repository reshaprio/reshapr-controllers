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

import io.reshapr.kubernetes.api.model.Status;
import io.sundr.builder.annotations.Buildable;

/**
 * This is the {@code status} sub-resource of a {@link Resource} custom resource.
 * It is updated by the operator to reflect the current synchronization state
 * with the Reshapr control plane.
 *
 * @author vaishnav
 */
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class ResourceStatus {

    private Status state;
    private String message;
    private String serviceId;
    private String artifactId;

    public Status getState() {
        return state;
    }

    public void setState(Status state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
}
