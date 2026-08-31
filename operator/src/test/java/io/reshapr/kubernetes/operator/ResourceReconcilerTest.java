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
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceAnnotations;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceItem;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceSpec;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceStatus;
import io.reshapr.kubernetes.api.resource.v1alpha1.ResourceTemplateItem;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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
        reconciler = new ResourceReconciler();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Resource buildResource(String name, String serviceName, String serviceVersion,
                                   Map<String, ResourceItem> resources,
                                   Map<String, ResourceTemplateItem> resourceTemplates) {
        ServiceRef serviceRef = new ServiceRef();
        serviceRef.setName(serviceName);
        serviceRef.setVersion(serviceVersion);

        ResourceSpec spec = new ResourceSpec();
        spec.setService(serviceRef);
        spec.setResources(resources);
        spec.setResourceTemplates(resourceTemplates);

        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("test-ns");

        Resource resource = new Resource();
        resource.setMetadata(meta);
        resource.setSpec(spec);
        return resource;
    }

    private ResourceItem buildResourceItem(String name, String description, String text) {
        ResourceItem item = new ResourceItem();
        item.setName(name);
        item.setDescription(description);
        item.setText(text);
        return item;
    }

    private ResourceTemplateItem buildTemplateItem(String name, String description) {
        ResourceTemplateItem item = new ResourceTemplateItem();
        item.setName(name);
        item.setDescription(description);
        return item;
    }

    // ── artifact type ─────────────────────────────────────────────────────────

    @Test
    void artifactType_isReshaprResources() {
        assertEquals(ArtifactType.RESHAPR_RESOURCES, reconciler.getArtifactType());
    }

    // ── service ref ───────────────────────────────────────────────────────────

    @Test
    void getServiceRef_returnsServiceFromSpec() {
        Resource resource = buildResource("my-resource", "GitHub GraphQL", "20250917", null, null);
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
        Resource resource = buildResource("github-api-resources-01", "svc", "v1", null, null);
        assertEquals("github-api-resources-01", reconciler.getArtifactName(resource));
    }

    // ── artifact content — resources ──────────────────────────────────────────

    @Test
    void getArtifactContent_producesCorrectJson_withResources() throws Exception {
        Map<String, ResourceItem> resources = new LinkedHashMap<>();
        resources.put("file:///users/{login}", buildResourceItem("user-profile", "A GitHub user", "Hello"));

        Resource resource = buildResource("res-01", "GitHub GraphQL", "20250917", resources, null);
        String json = reconciler.getArtifactContent(resource);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("reshapr.io/v1alpha1", root.get("apiVersion").asText());
        assertEquals("Resource", root.get("kind").asText());
        assertEquals("GitHub GraphQL", root.get("service").get("name").asText());

        JsonNode resourcesNode = root.get("resources");
        assertNotNull(resourcesNode);
        assertTrue(resourcesNode.has("file:///users/{login}"));
        assertEquals("user-profile", resourcesNode.get("file:///users/{login}").get("name").asText());
        assertEquals("A GitHub user", resourcesNode.get("file:///users/{login}").get("description").asText());
        assertFalse(root.has("resourceTemplates"), "resourceTemplates should be absent when null");
    }

    // ── artifact content — resourceTemplates ──────────────────────────────────

    @Test
    void getArtifactContent_producesCorrectJson_withResourceTemplates() throws Exception {
        Map<String, ResourceTemplateItem> templates = new LinkedHashMap<>();
        templates.put("file:///repos/{owner}/{repo}", buildTemplateItem("repo-template", "A GitHub repo template"));

        Resource resource = buildResource("res-02", "GitHub GraphQL", "20250917", null, templates);
        String json = reconciler.getArtifactContent(resource);
        JsonNode root = objectMapper.readTree(json);

        JsonNode templatesNode = root.get("resourceTemplates");
        assertNotNull(templatesNode);
        assertTrue(templatesNode.has("file:///repos/{owner}/{repo}"));
        assertEquals("repo-template", templatesNode.get("file:///repos/{owner}/{repo}").get("name").asText());
        assertFalse(root.has("resources"), "resources should be absent when null");
    }

    // ── artifact content — ResourceItem fields ────────────────────────────────

    @Test
    void getArtifactContent_includesAnnotations() throws Exception {
        ResourceAnnotations annotations = new ResourceAnnotations();
        annotations.setAudience(List.of("user", "assistant"));
        annotations.setPriority(0.8f);

        ResourceItem item = buildResourceItem("annotated", "with annotations", null);
        item.setAnnotations(annotations);
        item.setMimeType("text/plain");

        Map<String, ResourceItem> resources = Map.of("file:///annotated", item);
        Resource resource = buildResource("res-03", "svc", "v1", resources, null);
        String json = reconciler.getArtifactContent(resource);
        JsonNode root = objectMapper.readTree(json);

        JsonNode itemNode = root.get("resources").get("file:///annotated");
        assertEquals("text/plain", itemNode.get("mimeType").asText());
        JsonNode ann = itemNode.get("annotations");
        assertNotNull(ann);
        assertEquals(2, ann.get("audience").size());
        assertEquals(0.8f, ann.get("priority").floatValue(), 0.001f);
    }

    @Test
    void getArtifactContent_omitsNullFields() throws Exception {
        Resource resource = buildResource("res-04", "My Service", "1.0", null, null);
        String json = reconciler.getArtifactContent(resource);
        JsonNode root = objectMapper.readTree(json);
        assertFalse(root.has("resources"));
        assertFalse(root.has("resourceTemplates"));
    }

    // ── status update ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_setsAllFields() {
        Resource resource = buildResource("res-05", "svc", "v1", null, null);
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
        Resource resource = buildResource("res-06", "svc", "v1", null, null);
        reconciler.updateStatus(resource, "svc-123", "art-456", Status.READY, "OK");
        reconciler.updateStatus(resource, null, null, null, null);

        ResourceStatus status = resource.getStatus();
        assertEquals("svc-123", status.getServiceId());
        assertEquals("art-456", status.getArtifactId());
        assertEquals(Status.READY, status.getState());
        assertEquals("OK", status.getMessage());
    }
}
