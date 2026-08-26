# Service Custom Resource

## Overview

The `Service` Custom Resource (CR) allows you to declare a reShapr **Service** — the imported
representation of an API artifact (OpenAPI, gRPC, AsyncAPI, etc.) — as a first-class Kubernetes
object. The operator imports the artifact from the URL you specify into the target reShapr
control plane and keeps the corresponding Service in sync.

The `Service` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`services.reshapr.io-v1.yml`](../deploy/crd/services.reshapr.io-v1.yml).

At a higher level, a `Service` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Service
metadata:
  name: open-meteo-api
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  name: open-meteo-api
  version: 1.0.0
  url: https://raw.githubusercontent.com/open-meteo/open-meteo/refs/heads/main/openapi/forecast.yml
  secretRef: open-meteo-credentials
  includedOperations:
    - GET /v1/forecast
  keepOnDelete: false
```

`spec.url` is the only mandatory field: it points to the remote artifact that reShapr should
import. All other fields let you refine the imported Service.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing services with:

```sh
$ kubectl get services.reshapr.io -n my-ns
NAME            AGE
open-meteo-api   1d
```

and inspect a specific instance with `kubectl get service.reshapr.io/open-meteo-api -n my-ns -o yaml`.

## Status structure

The operator tracks the reconciliation state in the resource's `.status` field:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Service
metadata:
  name: open-meteo-api
spec:
  [...]
status:
  status: READY
  observedGeneration: 1
  serviceId: 66ca3b482a11675200f87792
  message: Service 'open-meteo-api:1.0.0' imported successfully
```

| Field                    | Description                                                                                     |
|--------------------------|-------------------------------------------------------------------------------------------------|
| `status.status`          | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.     |
| `status.message`         | Human-readable message giving details about the current status (e.g. error message).            |
| `status.observedGeneration` | The `metadata.generation` value at the time of the last successful reconciliation.           |
| `status.serviceId`       | The unique identifier of the corresponding Service resource in the reShapr control plane.       |

## Service specification details

| Property             | Description                                                                                                                         |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `url`                | **Mandatory**. The URL from which the reShapr control plane fetches the remote artifact definition (OpenAPI, gRPC, AsyncAPI, ...).  |
| `name`               | **Optional**. Override the Service name coming from the artifact. Defaults to the name declared in the artifact.                    |
| `version`            | **Optional**. Override the Service version coming from the artifact. Defaults to the version declared in the artifact.              |
| `includedOperations` | **Optional**. Exhaustive list of operations to import. If set, only these operations are exposed.                                   |
| `excludedOperations` | **Optional**. List of operations to skip when importing. Ignored when `includedOperations` is set.                                  |
| `secretRef`          | **Optional**. Name of a Reshapr `Secret` in the same organization whose credentials are used by the control plane to fetch the URL. |
| `keepOnDelete`       | **Optional**. When `true`, deleting the CR keeps the Service in the control plane. Defaults to `false`.                             |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: Service
metadata:
  name: pastries-api
  namespace: default
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  name: API Pastries
  version: 0.0.1
  url: https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastries-openapi.yaml
  excludedOperations:
    - DELETE /pastries/{name}
  keepOnDelete: true
```
