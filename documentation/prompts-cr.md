# Prompts Custom Resource

## Overview

The `Prompts` Custom Resource (CR) allows you to attach one or more **prompts** to an existing
reShapr [Service](./service-cr.md). Prompts are reusable, parameterized templates that
the control plane exposes alongside the Service as a `RESHAPR_PROMPTS` artifact — typically
consumed by MCP clients.

The `Prompts` CRD uses the `reshapr.io/v1alpha1` API version. The schema is available in
[`prompts.reshapr.io-v1.yml`](../deploy/crd/prompts.reshapr.io-v1.yml).

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Prompts
metadata:
  name: open-meteo-gitops-prompts
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  prompts:
    weather-summary:
      title: Weather Summary
      description: Summarize the weather forecast for a location
      arguments:
        - name: city
          description: The city to get the forecast for
          required: true
      result: Give me a short summary of the weather forecast for ${city}.
```

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md). The short name is `prompts`.

## Status structure

| Field               | Description                                                                             |
|---------------------|-----------------------------------------------------------------------------------------|
| `status.state`      | `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.                           |
| `status.message`    | Human-readable message giving details about the current status.                         |
| `status.serviceId`  | Identifier of the target Service in the reShapr control plane.                          |
| `status.artifactId` | Identifier of the prompts artifact within the target Service.                           |

## Specification details

| Property  | Description                                                                          |
|-----------|--------------------------------------------------------------------------------------|
| `service` | **Mandatory**. Target Service reference (`name` and `version`).                      |
| `prompts` | Map of prompt name → prompt item (see below).                                        |

### Prompt item

| Property      | Description                                                                         |
|---------------|-------------------------------------------------------------------------------------|
| `title`       | **Optional**. Human-readable title.                                                 |
| `description` | **Optional**. Human-readable description.                                           |
| `arguments`   | **Optional**. List of arguments: `name`, `title`, `description`, `required`.        |
| `result`      | **Mandatory**. The prompt result text. May be a template reusing `${argument}` placeholders. |
