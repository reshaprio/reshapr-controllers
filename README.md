# reShapr Controllers

Kubernetes controllers for automated operations of reShapr

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/reshaprio/reshapr-controllers/build-verify.yml?logo=github&style=for-the-badge)](https://github.com/reshaprio/reshapr-controllers/actions)
[![Container](https://img.shields.io/badge/dynamic/json?color=blueviolet&logo=docker&style=for-the-badge&label=Quay.io&query=tags[1].name&url=https://quay.io/api/v1/repository/reshapr/reshapr-operator/tag/?limit=10&page=1&onlyActiveTags=true)](https://quay.io/repository/reshapr/reshapr-operator?tab=tags)
[![License](https://img.shields.io/github/license/microcks/microcks-testcontainers-java?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/discord-reshapr-pink.svg?color=7289da&style=for-the-badge&logo=discord)](https://discord.gg/KyDUdam34h)
[![GitHub stars](https://img.shields.io/github/stars/reshaprio/reshapr-controllers?style=for-the-badge&logo=github&color=ffad05)](https://github.com/reshaprio/reshapr-controllers)

This repository aims to provide different Kubernetes components:

* Custom Resource Definitions (CRDs) to represent reShapr concepts in Kubernetes - with the Java library to manage them,
* Kubernetes Operator to manage the lifecycle of reShapr resources through CRDs,
* Admission Webhook to allow injection of a reShapr proxy as a sidecar container in application pods,
* Admission Webhook to allow further validation of reShapr resources when needed.

## Build Status

Current development version is `0.0.1`.

## Documentation

User-facing documentation lives under the [`documentation/`](./documentation/README.md) folder.
It covers installation, the instance connection flow, the admission controller and a reference
for each Custom Resource (`Service`, `GatewayGroup`, `ConfigurationPlan`, `Exposition`, `CustomTools`).