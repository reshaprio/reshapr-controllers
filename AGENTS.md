# AGENTS.md — reshapr-controllers

## Architecture

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the canonical module map, data flow diagram and design decisions.
Do not duplicate that content here — update `ARCHITECTURE.md` instead whenever the structure evolves.

TL;DR for agents: this is a multi-module Maven project (`io.reshapr:reshapr-controllers`) with three modules — `api/` 
(CRD definitions), `operator/` (Quarkus + JOSDK reconcilers + OpenAPI-generated client to the control plane) and 
`admission/` (Quarkus + JOSDK mutating webhook, standalone, no dependency on `api/`).

## Build & Run

Requires **Java 25** and Maven (use `./mvnw` wrapper).

```sh
# Full build from root
./mvnw clean install

# Build single module
./mvnw -pl api clean install
./mvnw -pl operator clean install -DskipTests
./mvnw -pl admission clean install

# Quarkus dev mode (operator or admission)
./mvnw -pl operator quarkus:dev
./mvnw -pl admission quarkus:dev

# Native build
./mvnw -pl admission clean install -Pnative
```

Integration tests are skipped by default (`skipITs=true`); the `native` profile enables them. No unit tests exist yet.

## Key Conventions

### CRD API Pattern (`api/` module)
Every custom resource follows this structure — use it when adding new CRDs:
1. `<Name>.java` — extends `CustomResource<NameSpec, NameStatus>`, annotated with `@Group("reshapr.io")`, `@Version("v1alpha1")`, `@Buildable`
2. `<Name>Spec.java` — POJO with `@JsonPropertyDescription` on every field, `@JsonPropertyOrder`, `@JsonIgnoreProperties(ignoreUnknown = true)`, `@JsonInclude(NON_NULL)`, `@Buildable`
3. `<Name>Status.java` — same annotation pattern
4. Package: `io.reshapr.kubernetes.api.<domain>.v1alpha1`

Example reference: `api/src/main/java/io/reshapr/kubernetes/api/tools/v1alpha1/CustomTools.java`

### Reconciler Pattern (`operator/` module)
Reconcilers implement `Reconciler<T>` from JOSDK. Package: `io.reshapr.kubernetes.operator`. No annotations needed — Quarkus operator SDK auto-discovers them.

### Admission Webhook Pattern (`admission/` module)
- Mutators implement `Mutator<T>` and are instantiated in `AdmissionControllers` (factory pattern)
- CDI wiring happens in `AdmissionControllerConfig` with `@Singleton` + `@Named`
- JAX-RS endpoint in `AdmissionEndpoint` exposes `POST /mutate`

### Code Style
- Apache 2.0 license header on every Java file
- `@author laurent` Javadoc tag on main classes
- No Lombok — manual getters/setters with `@Buildable` (Sundrio) for Fabric8-compatible builders

## Generated Code (do not edit)
- `operator/target/generated-sources/openapi/` — REST client generated from `operator/api/reshapr-public-openapi-v0.1.yaml`
- `api/target/classes/schemas/` — JSON schemas generated from CRD classes at build time

## Deployment
- Admission controller K8s manifests: `admission/k8s/` (ServiceAccount, Deployment, Service, RoleBinding, MutatingWebhookConfiguration, cert-manager Certificate/Issuer)
- Target namespace: `reshapr-system`
- Container images: `quay.io/lbroudoux/reshapr-admission-controller:nightly`
- Dockerfiles: `admission/src/main/docker/` (JVM and native variants)

