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

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;

/**
 * This is the API definition for Skills custom resource. It attaches Agent Skills to an existing
 * reShapr Service so that the gateway can serve them over MCP (Skills extension). It is synchronized
 * with the control plane as a {@code RESHAPR_SKILLS} artifact (available since reShapr 1.2.0).
 * @author vaishnav
 */
@Group("reshapr.io")
@Version("v1alpha1")
@ShortNames("skills")
@Plural("skills")
@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder", refs = {
      @BuildableReference(ObjectMeta.class) })
public class Skills extends CustomResource<SkillsSpec, SkillsStatus> implements Namespaced {
}

