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
package io.reshapr.kubernetes.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reshapr.client.model.ArtifactType;
import io.reshapr.kubernetes.api.model.ServiceRef;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.resource.v1alpha1.Resource;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceSpec;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResourceReconciler}.
 * Validates artifact type, content serialization and status update logic
 * without requiring a live Kubernetes cluster or control-plane connection.
 *
 * @author vaishnav
 */
class ResourceReconcilerTest {

    private ResourceReconciler reconciler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Use the package-private no-arg constructor (CDI proxy bypass)
        reconciler = new ResourceReconciler();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Resource buildResource(String name, String serviceName, String serviceVersion,
                                   Map<String, Object> resources) {
        ServiceRef serviceRef = new ServiceRef();
        serviceRef.setName(serviceName);
        serviceRef.setVersion(serviceVersion);

        ResourceSpec spec = new ResourceSpec();
        spec.setService(serviceRef);
        spec.setResources(resources);

        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("test-ns");

        Resource resource = new Resource();
        resource.setMetadata(meta);
        resource.setSpec(spec);
        return resource;
    }

    // ── artifact type ─────────────────────────────────────────────────────────

    @Test
    void artifactType_isReshaprResources() {
        assertEquals(ArtifactType.RESHAPR_RESOURCES, reconciler.getArtifactType());
    }

    // ── service ref ───────────────────────────────────────────────────────────

    @Test
    void getServiceRef_returnsServiceFromSpec() {
        Resource resource = buildResource("my-resource", "GitHub GraphQL", "20250917", null);
        ServiceRef ref = reconciler.getServiceRef(resource);
        assertNotNull(ref);
        assertEquals("GitHub GraphQL", ref.getName());
        assertEquals("20250917", ref.getVersion());
    }

    @Test
    void getServiceRef_returnsNull_whenSpecIsNull() {
        Resource resource = new Resource();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("empty");
        resource.setMetadata(meta);
        assertNull(reconciler.getServiceRef(resource));
    }

    // ── artifact name ─────────────────────────────────────────────────────────

    @Test
    void getArtifactName_returnsMetadataName() {
        Resource resource = buildResource("github-api-resources-01", "svc", "v1", null);
        assertEquals("github-api-resources-01", reconciler.getArtifactName(resource));
    }

    // ── artifact content ──────────────────────────────────────────────────────

    @Test
    void getArtifactContent_producesCorrectJson() throws Exception {
        Map<String, Object> resources = new LinkedHashMap<>();
        Map<String, Object> userResource = new LinkedHashMap<>();
        userResource.put("description", "A GitHub user");
        resources.put("user", userResource);

        Resource resource = buildResource("res-01", "GitHub GraphQL", "20250917", resources);
        String json = reconciler.getArtifactContent(resource);

        JsonNode root = objectMapper.readTree(json);
        assertEquals("reshapr.io/v1alpha1", root.get("apiVersion").asText());
        assertEquals("Resource", root.get("kind").asText());

        JsonNode service = root.get("service");
        assertNotNull(service, "service field must be present");
        assertEquals("GitHub GraphQL", service.get("name").asText());
        assertEquals("20250917", service.get("version").asText());

        JsonNode resourcesNode = root.get("resources");
        assertNotNull(resourcesNode, "resources field must be present");
        assertTrue(resourcesNode.has("user"), "resources must contain 'user' key");
        assertEquals("A GitHub user", resourcesNode.get("user").get("description").asText());
    }

    @Test
    void getArtifactContent_omitsNullFields() throws Exception {
        Resource resource = buildResource("res-02", "My Service", "1.0", null);
        String json = reconciler.getArtifactContent(resource);
        JsonNode root = objectMapper.readTree(json);

        // resources key must be absent when null (JsonInclude.NON_NULL)
        assertFalse(root.has("resources"), "resources field should be absent when null");
    }

    // ── status update ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_setsAllFields() {
        Resource resource = buildResource("res-03", "svc", "v1", null);
        reconciler.updateStatus(resource, "svc-123", "art-456", Status.READY, "Synchronized");

        ResourceStatus status = resource.getStatus();
        assertNotNull(status);
        assertEquals(Status.READY, status.getState());
        assertEquals("svc-123", status.getServiceId());
        assertEquals("art-456", status.getArtifactId());
        assertEquals("Synchronized", status.getMessage());
    }

    @Test
    void updateStatus_doesNotOverwrite_whenFieldsAreNull() {
        Resource resource = buildResource("res-04", "svc", "v1", null);

        // First call sets everything
        reconciler.updateStatus(resource, "svc-123", "art-456", Status.READY, "OK");
        // Second call with nulls must not wipe existing values
        reconciler.updateStatus(resource, null, null, null, null);

        ResourceStatus status = resource.getStatus();
        assertEquals("svc-123", status.getServiceId());
        assertEquals("art-456", status.getArtifactId());
        assertEquals(Status.READY, status.getState());
        assertEquals("OK", status.getMessage());
    }
}
