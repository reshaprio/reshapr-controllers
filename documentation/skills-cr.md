# Skills Custom Resource

## Overview

The `Skills` Custom Resource (CR) allows you to attach one or more **Agent Skills** to an existing
reShapr [Service](./service-cr.md). A skill is a directory holding a `SKILL.md` file (and optional
supporting files) hosted on an HTTP(S) server. The control plane stores the definition as a
`RESHAPR_SKILLS` artifact, then the gateway fetches the skill files and serves them over MCP as
`skill://` resources, following the
[Skills over MCP](https://github.com/modelcontextprotocol/experimental-ext-skills) extension.

This way, an MCP server can ship both its tools and the instructions on how to use them, as a single unit.

> **Note:** `Skills` artifacts require a reShapr control plane `1.2.0` or later.

The `Skills` CRD uses the `reshapr.io/v1alpha1` API version. The schema is available in
[`skills.reshapr.io-v1.yml`](../deploy/crd/skills.reshapr.io-v1.yml).

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Skills
metadata:
  name: open-meteo-gitops-skills
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: '1.0'
  skills:
    open-meteo-forecast:
      remoteContent: https://skills.example.com/open-meteo-forecast
      path: weather/forecast
      files:
        - references/VARIABLES.md
      _meta:
        owner: weather-team
```

With the example above, the gateway fetches:

* `https://skills.example.com/open-meteo-forecast/SKILL.md` (always, implicit), and
* `https://skills.example.com/open-meteo-forecast/references/VARIABLES.md`.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md). The short name is `skills`.

A complete sample is available in
[`open-meteo-gitops-skills.yaml`](../deploy/samples/open-meteo-gitops-skills.yaml).

## Status structure

| Field               | Description                                                                             |
|---------------------|-----------------------------------------------------------------------------------------|
| `status.state`      | `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.                           |
| `status.message`    | Human-readable message giving details about the current status.                         |
| `status.serviceId`  | Identifier of the target Service in the reShapr control plane.                          |
| `status.artifactId` | Identifier of the skills artifact within the target Service.                            |

## Specification details

| Property       | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| `service`      | **Mandatory**. Target Service reference (`name` and `version`).                      |
| `skills`       | Map of skill name → skill item (see below).                                          |
| `keepOnDelete` | **Optional**. When `true`, deleting the CR keeps the artifact in the control plane. Defaults to `false`. |

Skill names (the map keys) must match the `name` declared in the `SKILL.md` frontmatter.

### Skill item

| Property        | Description                                                                         |
|-----------------|-------------------------------------------------------------------------------------|
| `remoteContent` | **Mandatory**. HTTP(S) URL of the skill directory — the one holding `SKILL.md`.     |
| `path`          | **Optional**. Organizational prefix of the skill URI, without leading or trailing slash (e.g. `acme/billing`). |
| `files`         | **Optional**. Supporting files, as paths relative to the skill directory. `SKILL.md` is implicit and must not be listed. |
| `_meta`         | **Optional**. Free-form metadata attached to the `SKILL.md` resource.               |

