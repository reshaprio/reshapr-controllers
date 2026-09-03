# reShapr Controllers

Manage your reShapr resources the Kubernetes-native way — CRDs, an Operator and an Admission
Webhook shipped in one repo.

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/reshaprio/reshapr-controllers/build-verify.yml?logo=github&style=for-the-badge)](https://github.com/reshaprio/reshapr-controllers/actions)
[![Operator Container](https://img.shields.io/badge/dynamic/json?color=blueviolet&logo=docker&style=for-the-badge&label=Quay.io%20operator&query=tags[1].name&url=https://quay.io/api/v1/repository/reshapr/reshapr-operator/tag/?limit=10&page=1&onlyActiveTags=true)](https://quay.io/repository/reshapr/reshapr-operator?tab=tags)
[![Admission Container](https://img.shields.io/badge/dynamic/json?color=blueviolet&logo=docker&style=for-the-badge&label=Quay.io%20admission&query=tags[1].name&url=https://quay.io/api/v1/repository/reshapr/reshapr-admission/tag/?limit=10&page=1&onlyActiveTags=true)](https://quay.io/repository/reshapr/reshapr-admission-controller?tab=tags)
[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-%E2%89%A51.27-blue?style=for-the-badge&logo=kubernetes)](https://kubernetes.io/)
[![License](https://img.shields.io/github/license/reshaprio/reshapr-controllers?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/discord-reshapr-pink.svg?color=7289da&style=for-the-badge&logo=discord)](https://discord.gg/KyDUdam34h)
[![GitHub stars](https://img.shields.io/github/stars/reshaprio/reshapr-controllers?style=for-the-badge&logo=github&color=ffad05)](https://github.com/reshaprio/reshapr-controllers)

This repository provides three complementary Kubernetes components:

* **Custom Resource Definitions (CRDs)** to represent reShapr concepts in Kubernetes — with the Java library to manage them.
* **Kubernetes Operator** to manage the lifecycle of reShapr resources through CRDs.
* **Admission Webhook** to inject a reShapr proxy as a sidecar container in application Pods.

## Build Status

Latest release version is `0.0.1`.

Current development version is `0.0.2-SNAPSHOT`.

## How it works

`reshapr-controllers` drives an **existing** reShapr control plane declaratively via
Kubernetes Custom Resources. It does not deploy the control plane itself — see the Quick start
below for the recommended way to install it. Once the control plane is running, the operator
reconciles reShapr CRs against its REST API, while the admission webhook injects reShapr proxy
sidecars into opt-in application Pods.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the module map, data flow diagram and design
decisions.

## Quick start

> [!NOTE]
> The Quick start below assumes a **reShapr control plane** is already installed in the
> `reshapr-system` namespace. If it is not yet the case, deploy it first using the Helm charts
> available at [reshaprio/reshapr-helm-charts](https://github.com/reshaprio/reshapr-helm-charts).

Install the CRDs, the operator and a sample resource on a Kubernetes cluster:

```sh
kubectl apply -f deploy/crd/
kubectl apply -f deploy/operator.yaml -n reshapr-system
kubectl apply -f deploy/samples/open-meteo-api-service.yaml -n reshapr-system
```

Then check the reconciliation status:

```sh
kubectl get services.reshapr.io -n reshapr-system
```

For the full installation guide (operator + admission webhook), see
[`documentation/installation-operator.md`](./documentation/installation-operator.md) and
[`documentation/installation-admission.md`](./documentation/installation-admission.md).

## Documentation

User-facing documentation lives under the [`documentation/`](./documentation/README.md) folder.
It covers installation, the instance connection flow, the admission controller and a reference
for each Custom Resource (`Service`, `GatewayGroup`, `ConfigurationPlan`, `Exposition`,
`SecretSource`, `CustomTools`, `Resource`).

## License

This project is licensed under the [Apache License 2.0](./LICENSE).