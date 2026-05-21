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
package io.reshapr.kubernetes.operator.auth;

/**
 * Constants for Reshapr custom resource annotations.
 * @author laurent
 */
public final class ReshaprAnnotations {

   /** Annotation key indicating the Kubernetes Service name of the reShapr control plane instance. */
   public static final String INSTANCE = "reshapr.io/instance";

   /** Annotation key indicating the organization to impersonate when calling the control plane. */
   public static final String ORGANIZATION = "reshapr.io/organization";

   private ReshaprAnnotations() {
      // Utility class — no instantiation.
   }
}

