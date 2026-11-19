# ToolsOutputFilters Custom Resource

## Overview

The `ToolsOutputFilters` Custom Resource (CR) allows you to attach **output filters** to the tools of an
existing reShapr [Service](./service-cr.md). Filters slim down and transform tool responses before they reach
LLMs, reducing payload size and token usage. They are synchronized with the control plane as a
`RESHAPR_TOOLS_OUTPUT_FILTERS` artifact.

The `ToolsOutputFilters` CRD uses the `reshapr.io/v1alpha1` API version. The schema is available in
[`toolsoutputfilters.reshapr.io-v1.yml`](../deploy/crd/toolsoutputfilters.reshapr.io-v1.yml).

```yaml
apiVersion: reshapr.io/v1alpha1
kind: ToolsOutputFilters
metadata:
  name: open-meteo-gitops-filters
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  filters:
    getForecast:
      jsonRetain:
        - /daily
        - /current_weather
      jsonPatches:
        - op: remove
          path: /daily/time
      compact: true
      convertToToon: true
```

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md). The short name is `tof`.

## Status structure

| Field               | Description                                                                 |
|---------------------|-----------------------------------------------------------------------------|
| `status.state`      | `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.               |
| `status.message`    | Human-readable message giving details about the current status.             |
| `status.serviceId`  | Identifier of the target Service in the reShapr control plane.              |
| `status.artifactId` | Identifier of the output filters artifact within the target Service.        |

## Specification details

| Property       | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| `service`      | **Mandatory**. Target Service reference (`name` and `version`).                      |
| `filters`      | Map of tool name → filter item (see below).                                          |
| `keepOnDelete` | **Optional**. When `true`, deleting the CR keeps the artifact in the control plane. Defaults to `false`. |

### Filter item

At least one of the following properties must be set.

| Property        | Description                                                                         |
|-----------------|-------------------------------------------------------------------------------------|
| `jsonRetain`    | List of JSON Pointer paths to retain; everything else is removed.                   |
| `jsonPatches`   | List of JSON Patch (RFC 6902) operations: `op`, `path`, `value`, `from`.            |
| `compact`       | Remove null values, empty strings, empty arrays and empty objects.                  |
| `convertToToon` | Convert the final JSON output to Toon format.                                       |
