# ConfigurationPlan Custom Resource

## Overview

The `ConfigurationPlan` Custom Resource (CR) allows you to publish a reShapr
[Service](./service-cr.md) onto a backend endpoint by describing how the reShapr gateway should
route to it and how to authenticate calls (API Key, OAuth2, ...). A Configuration Plan is thus
the **binding between a Service and a backend implementation**, ready to be consumed by client
applications.

The `ConfigurationPlan` CRD is defined using the `reshapr.io/v1alpha1` API version. The full
schema definition is available in
[`configurationplans.reshapr.io-v1.yml`](../deploy/crd/configurationplans.reshapr.io-v1.yml).

At a higher level, a `ConfigurationPlan` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: ConfigurationPlan
metadata:
  name: open-meteo-plan
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: open-meteo-api
    version: 1.0.0
  backendEndpoint: https://api.open-meteo.com
  apiKey: true
  oauth2:
    clientId: my-client
    clientSecret: my-secret
```

`spec.service` and `spec.backendEndpoint` are mandatory — the plan needs to know which Service
it configures and where to route traffic.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing configuration plans with:

```sh
$ kubectl get configurationplans.reshapr.io -n my-ns
NAME              AGE
open-meteo-plan    1d
```

You can also use the short name `configplan`.

## Status structure

```yaml
apiVersion: reshapr.io/v1alpha1
kind: ConfigurationPlan
metadata:
  name: open-meteo-plan
spec:
  [...]
status:
  status: READY
  observedGeneration: 1
  configurationPlanId: 66ca3b482a11675200f87792
  message: ConfigurationPlan reconciled
```

| Field                          | Description                                                                                     |
|--------------------------------|-------------------------------------------------------------------------------------------------|
| `status.status`                | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.     |
| `status.message`               | Human-readable message giving details about the current status.                                 |
| `status.observedGeneration`    | The `metadata.generation` value at the time of the last successful reconciliation.              |
| `status.configurationPlanId`   | Unique identifier of the corresponding ConfigurationPlan in the reShapr control plane.          |

## ConfigurationPlan specification details

| Property           | Description                                                                                                                                                                                                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `service`          | **Mandatory**. Reference to the target [Service](./service-cr.md) this plan configures. See _Service reference_ below.                                                                                                                                                       |
| `backendEndpoint`  | **Mandatory**. URL of the backend implementation the reShapr gateway should route traffic to.                                                                                                                                                                                |
| `apiKey`           | **Optional**. When `true`, the control plane generates an API Key for this plan. Defaults to `false`.                                                                                                                                                                        |
| `oauth2`           | **Optional**. OAuth2 client credentials to attach to this plan. See _OAuth2 specification_ below.                                                                                                                                                                            |
| `artifacts`        | **Optional**. List of selected attached artifact names. Empty (or absent) means all the attached artifacts of the service apply. The service main artifact is never impacted.                                                                                                |
| `audit`               | **Optional**. When `true`, the gateway logs each call routed through this plan into the audit log. Defaults to `false`.                                                                                                                                                    |
| `includedOperations`  | **Optional**. List of operation names to explicitly expose through this plan. When provided, only these operations are routed; all others are rejected. Mutually exclusive with `excludedOperations`.                                                                       |
| `excludedOperations`  | **Optional**. List of operation names to exclude from this plan. When provided, every operation of the Service is exposed except those listed. Mutually exclusive with `includedOperations`.                                                                                |
| `cachePolicy`      | **Optional**. Caching configuration applied by the gateway. See _Cache policy_ below. When absent the proxy falls back to its built-in defaults.                                                                                                                             |
| `headerPolicy`     | **Optional**. Header propagation policy controlling which request/response headers are forwarded to/from the backend. See _Header policy_ below. When absent the proxy applies its built-in defaults (e.g. `Authorization` and `Cookie` are stripped from backend requests). |

### Service reference (`spec.service`)

| Property   | Description                                                                       |
|------------|-----------------------------------------------------------------------------------|
| `name`     | **Mandatory**. Human-readable name of the target Service.                          |
| `version`  | **Mandatory**. Human-readable version of the target Service.                       |

### OAuth2 specification (`spec.oauth2`)

| Property        | Description                                        |
|-----------------|----------------------------------------------------|
| `clientId`      | OAuth2 client identifier used to call the backend. |
| `clientSecret`  | OAuth2 client secret used to call the backend.     |

> [!WARNING]
> Do not store production OAuth2 client secrets in plain text in Git repositories. Prefer using
> a `Secret`-backed workflow (e.g. Sealed Secrets, External Secrets Operator) to inject the
> credentials into your CR before applying.

### Cache policy (`spec.cachePolicy`)

| Property      | Description                                                                       |
|---------------|-----------------------------------------------------------------------------------|
| `ttlMs`       | Time-to-live in milliseconds for client-side MCP caching.                          |
| `cacheScope`  | Cache scope to advertise (e.g. `public` or `private`).                             |

### Header policy (`spec.headerPolicy`)

Controls which HTTP headers the gateway propagates. Only the `request` direction is honored today;
the `response` direction is reserved for future use. When `headerPolicy` is absent, the proxy
applies its built-in defaults (notably, `Authorization` and `Cookie` are stripped from backend
requests).

| Property    | Description                                                                                                     |
|-------------|-----------------------------------------------------------------------------------------------------------------|
| `request`   | **Optional**. Allow/deny/rename directives applied to headers forwarded to the backend. See _Header rules_ below. |
| `response`  | **Optional**. Allow/deny/rename directives applied to headers returned from the backend. Reserved for future use. |

#### Header rules (`spec.headerPolicy.request` / `spec.headerPolicy.response`)

| Property   | Description                                                                                                                       |
|------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `allow`    | **Optional**. List of header names explicitly allowed to be propagated. When provided, only these headers are forwarded (minus any restricted by default). |
| `deny`     | **Optional**. List of header names explicitly denied from being propagated. Takes precedence over the allow-list.                  |
| `rename`   | **Optional**. Rename directives moving an incoming header to a different name before propagation. See _Header rename_ below.       |

#### Header rename (`spec.headerPolicy.request.rename[]`)

| Property   | Description                                                        |
|------------|--------------------------------------------------------------------|
| `from`     | **Mandatory**. Name of the incoming header to rename.               |
| `to`       | **Mandatory**. Target header name the value is propagated under.    |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: ConfigurationPlan
metadata:
  name: pastries-prod-plan
  namespace: default
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  service:
    name: API Pastries
    version: 0.0.1
  backendEndpoint: https://pastries.prod.acme.com
  apiKey: true
  oauth2:
    clientId: pastries-gateway
    clientSecret: change-me
  headerPolicy:
    request:
      allow:
        - X-Request-Id
      deny:
        - X-Internal-Debug
      rename:
        - from: X-Client-Id
          to: X-Consumer-Id
```
