# Resource Custom Resource

## Overview

The `Resource` Custom Resource (CR) allows you to attach one or more **resources** and
**resource templates** to an existing reShapr [Service](./service-cr.md). Resources are static
or remote content items (text, binary blobs, remote URIs) that the reShapr control plane exposes
alongside the Service as `RESHAPR_RESOURCES` artifacts — typically consumed by MCP clients.

Two families of items can be declared:

* **Resources** — concrete items identified by a fully-qualified URI. Their content can be
  inlined (`text` or `blob`) or referenced remotely (`remoteContent`).
* **Resource templates** — URI templates (RFC 6570) that describe families of resources that
  the control plane can materialize at runtime. Templates only carry metadata (name, title,
  description, MIME type, icons, annotations) — no content.

The `Resource` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`resources.reshapr.io-v1.yml`](../deploy/crd/resources.reshapr.io-v1.yml).

At a higher level, a `Resource` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Resource
metadata:
  name: open-meteo-gitops-resource
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  resources:
    <uri>:
      <resource-item>
  resourceTemplates:
    <uri-template>:
      <resource-template-item>
```

`spec.service` identifies the Service the resources are attached to (via a name + version pair).
At least one of `spec.resources` or `spec.resourceTemplates` must be present.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing resource attachments with:

```sh
$ kubectl get resources.reshapr.io -n my-ns
NAME                          AGE
open-meteo-gitops-resource     1d
```

You can also use the short name `rsrc`.

## Status structure

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Resource
metadata:
  name: open-meteo-gitops-resource
spec:
  [...]
status:
  state: READY
  serviceId: 66ca3b482a11675200f87792
  artifactId: 66ca3b482a11675200f87793
  message: Resources attached to Service 'open-meteo-api:1.0'
```

| Field              | Description                                                                                     |
|--------------------|-------------------------------------------------------------------------------------------------|
| `status.state`     | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.     |
| `status.message`   | Human-readable message giving details about the current status.                                 |
| `status.serviceId` | Identifier of the target Service in the reShapr control plane.                                  |
| `status.artifactId`| Identifier of the artifact holding the resources within the target Service.                     |

## Resource specification details

| Property            | Description                                                                                                                                                            |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `service`           | **Mandatory**. Reference to the target Service — see _Service reference_ below.                                                                                        |
| `resources`         | **Optional**. Map of URI → [_Resource item_](#resource-item-specification). At least one of `resources` or `resourceTemplates` must be present.                         |
| `resourceTemplates` | **Optional**. Map of URI template → [_Resource template item_](#resource-template-item-specification). At least one of `resources` or `resourceTemplates` must be present. |

### Service reference (`spec.service`)

| Property   | Description                                                                       |
|------------|-----------------------------------------------------------------------------------|
| `name`     | **Mandatory**. Human-readable name of the target Service.                          |
| `version`  | **Mandatory**. Human-readable version of the target Service.                       |

### Resource item specification

Each entry under `spec.resources` follows this schema. The map key is the URI identifying the
resource (e.g. `file:///docs/getting-started.md`, `https://example.com/logo.png`).

| Property         | Description                                                                                                                                                                    |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`           | **Mandatory**. Name of the resource.                                                                                                                                            |
| `title`          | **Optional**. Human-readable title of the resource.                                                                                                                             |
| `description`    | **Optional**. Human-readable long description of the resource.                                                                                                                  |
| `mimeType`       | **Optional**. MIME type of the resource content (e.g. `text/markdown`, `application/json`, `image/png`).                                                                        |
| `size`           | **Optional**. Size of the resource content in bytes.                                                                                                                            |
| `text`           | **Optional / exclusive**. Inlined textual content. Mutually exclusive with `blob` and `remoteContent`.                                                                          |
| `blob`           | **Optional / exclusive**. Base64-encoded binary content. Mutually exclusive with `text` and `remoteContent`.                                                                    |
| `remoteContent`  | **Optional / exclusive**. Remote URI from which the control plane can fetch the content. Mutually exclusive with `text` and `blob`.                                             |
| `icons`          | **Optional**. List of [icon entries](#icon-entry) associated with this resource.                                                                                                |
| `annotations`    | **Optional**. Additional [annotations](#annotations) (audience, priority, last modified date).                                                                                  |

> [!NOTE]
> Content is provided via **exactly one** of `text`, `blob` or `remoteContent`. Setting more
> than one is invalid and rejected by the reShapr control plane.

### Resource template item specification

Each entry under `spec.resourceTemplates` follows this schema. The map key is the URI template
(RFC 6570, e.g. `file:///forecast/{latitude}/{longitude}`).

| Property       | Description                                                                                       |
|----------------|---------------------------------------------------------------------------------------------------|
| `name`         | **Mandatory**. Name of the resource template.                                                     |
| `title`        | **Optional**. Human-readable title of the resource template.                                      |
| `description`  | **Optional**. Human-readable long description of the resource template.                           |
| `mimeType`     | **Optional**. MIME type of the resources materialized from this template.                         |
| `icons`        | **Optional**. List of [icon entries](#icon-entry) associated with this template.                  |
| `annotations`  | **Optional**. Additional [annotations](#annotations) (audience, priority, last modified date).    |

### Icon entry

| Property    | Description                                                                     |
|-------------|---------------------------------------------------------------------------------|
| `src`       | **Mandatory**. Source URI of the icon.                                          |
| `mimeType`  | **Optional**. MIME type of the icon (e.g. `image/svg+xml`, `image/png`).        |
| `sizes`     | **Optional**. List of icon sizes (e.g. `["32x32", "64x64"]`).                   |

### Annotations

| Property        | Description                                                                                       |
|-----------------|---------------------------------------------------------------------------------------------------|
| `audience`      | **Optional**. Intended audiences for this resource — typically `user`, `assistant`, or both.      |
| `priority`      | **Optional**. Importance of this resource, from `0.0` (least) to `1.0` (most).                    |
| `lastModified`  | **Optional**. Last modified date-time in ISO 8601 format.                                         |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Resource
metadata:
  name: open-meteo-gitops-resource
  namespace: default
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  resources:
    "file:///docs/getting-started.md":
      name: getting-started
      title: Getting Started
      description: Quick introduction to the Open Meteo API
      mimeType: text/markdown
      text: |
        # Getting Started

        Welcome to the Open Meteo API…
      annotations:
        audience:
          - user
          - assistant
        priority: 0.9
    "https://example.com/openmeteo-logo.png":
      name: logo
      title: Open Meteo logo
      mimeType: image/png
      remoteContent: https://example.com/openmeteo-logo.png
      icons:
        - src: https://example.com/openmeteo-icon-32.png
          mimeType: image/png
          sizes:
            - 32x32
  resourceTemplates:
    "file:///forecast/{latitude}/{longitude}":
      name: forecast
      title: Weather Forecast
      description: Weather forecast data for a specific location
      mimeType: application/json
      annotations:
        audience:
          - assistant
        priority: 0.7
```
