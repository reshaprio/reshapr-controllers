# reShapr Controllers Documentation

Welcome to the **reShapr Controllers** documentation. This folder gathers the user-oriented
references you need to understand, install, and operate the Kubernetes components shipped by
this repository.

The reShapr Controllers repository provides three complementary Kubernetes building blocks that
extend a running reShapr **control plane** with a Kubernetes-native experience:

* **Custom Resource Definitions (CRDs)** representing reShapr concepts (Services, Gateway Groups,
  Configuration Plans, Custom Tools) as first-class Kubernetes objects,
* A **Kubernetes Operator** that reconciles those CRDs against a target reShapr control plane
  instance,
* An **Admission Webhook** that mutates application Pods to inject a reShapr proxy sidecar and
  can be extended for validation.

## Table of contents

### Getting started

* [Installation & prerequisites](installation-operator.md) — install the CRDs, the operator on a Kubernetes cluster.
* [Instance connection flow](./instance-connection.md) — how CRs target a reShapr control plane
  instance and how the operator authenticates.
* [Admission Controller Installation](installation-admission.md) — install the Admission Webhook on a Kubernetes cluster.

### Custom Resources reference

| Kind                 | Scope       | Description                                                                 |
|----------------------|-------------|-----------------------------------------------------------------------------|
| [`Service`](./service-cr.md)                       | Namespaced | Import an API artifact (OpenAPI, gRPC, etc.) as a reShapr Service.          |
| [`GatewayGroup`](./gatewaygroup-cr.md)             | Namespaced | Declare a group of gateway instances identified by labels.                  |
| [`ConfigurationPlan`](./configurationplan-cr.md)   | Namespaced | Bind a Service to a backend endpoint with credentials (API Key, OAuth2).    |
| [`Exposition`](./exposition-cr.md)                 | Namespaced | Expose a Service on a GatewayGroup through a specific ConfigurationPlan.    |
| [`CustomTools`](./customtools-cr.md)               | Namespaced | Attach declarative or scripted custom tools to an existing Service.         |

### Admission webhook

* [Admission controller](./admission-controller.md) — how the Pod mutating webhook is deployed
  and how it augments application Pods.

## Conventions used in this documentation

Throughout this documentation you will encounter the following conventions:

* All Custom Resources use the API group `reshapr.io` and the version `v1alpha1`.
* All operator-managed resources use two annotations to target a reShapr control plane instance:
  * `reshapr.io/instance` — the qualified name of the control plane Kubernetes Service
    (typically `<name>.<namespace>`, e.g. `reshapr-control-plane-ctrl.reshapr-system`).
  * `reshapr.io/organization` — the reShapr organization the resource belongs to.
* The reconciliation state is exposed through the `.status.status` field and can take one of the
  following values: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, `ERROR`.

## Additional resources

* Repository top-level [`README.md`](../README.md)
* Contribution guide: [`CONTRIBUTING.md`](../CONTRIBUTING.md)
* Sample manifests: [`deploy/samples/`](../deploy/samples/)
* CRD schemas: [`deploy/crd/`](../deploy/crd/)
