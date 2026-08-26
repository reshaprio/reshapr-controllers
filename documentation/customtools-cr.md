# CustomTools Custom Resource

## Overview

The `CustomTools` Custom Resource (CR) allows you to attach one or more **custom tools** to an
existing reShapr [Service](./service-cr.md). Custom tools extend a Service with either:

* a **declarative mapping** — an alias for a target tool with a curated set of arguments, or
* a **scripted orchestration** — a script that composes several tools from one or several
  Services into a single higher-level tool.

The `CustomTools` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`customtools.reshapr.io-v1.yml`](../deploy/crd/customtools.reshapr.io-v1.yml).

At a higher level, a `CustomTools` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: CustomTools
metadata:
  name: pastries-custom-tools
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: API Pastries
    version: 0.0.1
  customTools:
    <tool-name>:
      <custom-tool-item>
    <tool-name>:
      <custom-tool-item>
```

`spec.service` identifies the Service the tools are attached to (via a name + version pair) and
`spec.customTools` is a map where the key is the human-readable tool name and the value is a
[_Custom tool item_](#custom-tool-item-specification).

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing custom tools with:

```sh
$ kubectl get customtools.reshapr.io -n my-ns
NAME                    AGE
pastries-custom-tools    1d
```

## Status structure

```yaml
apiVersion: reshapr.io/v1alpha1
kind: CustomTools
metadata:
  name: pastries-custom-tools
spec:
  [...]
status:
  state: READY
  serviceId: 66ca3b482a11675200f87792
  artifactId: 66ca3b482a11675200f87793
  message: Custom tools attached to Service 'API Pastries:0.0.1'
```

| Field              | Description                                                                                     |
|--------------------|-------------------------------------------------------------------------------------------------|
| `status.state`     | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.     |
| `status.message`   | Human-readable message giving details about the current status.                                 |
| `status.serviceId` | Identifier of the target Service in the reShapr control plane.                                  |
| `status.artifactId`| Identifier of the artifact holding the custom tools within the target Service.                  |

## CustomTools specification details

| Property       | Description                                                                                          |
|----------------|------------------------------------------------------------------------------------------------------|
| `service`      | **Mandatory**. Reference to the target Service — see _Service reference_ below.                      |
| `customTools`  | **Mandatory**. Map of tool name → [_Custom tool item_](#custom-tool-item-specification).             |

### Service reference (`spec.service`)

| Property   | Description                                                                       |
|------------|-----------------------------------------------------------------------------------|
| `name`     | **Mandatory**. Human-readable name of the target Service.                          |
| `version`  | **Mandatory**. Human-readable version of the target Service.                       |

### Custom tool item specification

Each entry under `spec.customTools` follows this schema. A custom tool is either **declarative**
(uses `tool` + `arguments`) or **scripted** (uses `script` + `tools`) — never both.

| Property       | Description                                                                                                                                                                       |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tool`         | **Optional / declarative**. The name of the target tool this custom tool aliases (e.g. `createIssue`).                                                                             |
| `arguments`    | **Optional / declarative**. Argument template for the target tool. Values can reference the custom input via the `${input.<name>}` notation.                                       |
| `script`       | **Optional / scripted**. Custom logic that orchestrates several tools.                                                                                                             |
| `tools`        | **Required with `script`**. Allow-list of tools the script can call. Each entry is a [_tool reference_](#tool-reference). Also used to pre-check elicitation secrets.              |
| `description`  | **Optional**. Human-readable long description of this custom tool.                                                                                                                |
| `input`        | **Optional**. A JSON Schema object describing the parameters the tool expects — see _Input schema_ below.                                                                          |

#### Tool reference

| Property   | Description                                                                                                                                                                     |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tool`     | **Mandatory**. Name of the target tool the script is allowed to call.                                                                                                            |
| `service`  | **Optional**. Target Service for cross-service calls, expressed as `<service_name:service_version>`. Omit for a tool belonging to the same Service.                              |

#### Input schema (`input`)

The `input` field follows a subset of the JSON Schema specification.

| Property     | Description                                                                                       |
|--------------|---------------------------------------------------------------------------------------------------|
| `type`       | **Mandatory**. Must be `object`.                                                                   |
| `properties` | **Mandatory**. Map of property name → JSON Schema property definition.                             |
| `required`   | **Optional**. List of required property names.                                                     |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: CustomTools
metadata:
  name: pastries-custom-tools
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: API Pastries
    version: 0.0.1
  customTools:
    getMillefeuille:
      description: Fetch the details of the Millefeuille pastry
      tool: getPastryByName
      arguments:
        name: Millefeuille
    createIssueForBrokenPastry:
      description: Report a broken pastry as an issue on the tracker
      input:
        type: object
        properties:
          pastryName:
            type: string
          reason:
            type: string
        required:
          - pastryName
          - reason
      script: |
        const pastry = await getPastryByName({ name: input.pastryName });
        await createIssue({
          title: `Pastry ${input.pastryName} broken`,
          body: `${input.reason} (stock=${pastry.stock})`
        });
      tools:
        - tool: getPastryByName
        - tool: createIssue
          service: Issue Tracker:1.0.0
```
