# GatewayGroup Custom Resource

## Overview

The `GatewayGroup` Custom Resource (CR) allows you to declare a **group of reShapr gateway
instances** in a Kubernetes-native way. A gateway group is identified by a set of labels that
gateways use to opt-in to the group, and it acts as a routing/deployment target when publishing
[Configuration Plans](./configurationplan-cr.md).

The `GatewayGroup` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`gatewaygroups.reshapr.io-v1.yml`](../deploy/crd/gatewaygroups.reshapr.io-v1.yml).

At a higher level, a `GatewayGroup` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: GatewayGroup
metadata:
  name: qa-gateway-group
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  name: qa-gateway-group
  labels:
    env: qa
    region: us
    team: reshapr
  keepOnDelete: false
```

`spec.labels` is the only meaningful field required to identify participating gateways.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing gateway groups with:

```sh
$ kubectl get gatewaygroups.reshapr.io -n my-ns
NAME               AGE
qa-gateway-group    1d
```

and inspect a specific instance with `kubectl get gatewaygroup/qa-gateway-group -n my-ns -o yaml`.

## Status structure

```yaml
apiVersion: reshapr.io/v1alpha1
kind: GatewayGroup
metadata:
  name: qa-gateway-group
spec:
  [...]
status:
  status: READY
  observedGeneration: 1
  gatewayGroupId: 66ca3b482a11675200f87792
  message: GatewayGroup 'qa-gateway-group' reconciled
```

| Field                        | Description                                                                                     |
|------------------------------|-------------------------------------------------------------------------------------------------|
| `status.status`              | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.     |
| `status.message`             | Human-readable message giving details about the current status.                                 |
| `status.observedGeneration`  | The `metadata.generation` value at the time of the last successful reconciliation.              |
| `status.gatewayGroupId`      | Unique identifier of the corresponding GatewayGroup in the reShapr control plane.               |

## GatewayGroup specification details

| Property       | Description                                                                                                                     |
|----------------|---------------------------------------------------------------------------------------------------------------------------------|
| `labels`       | **Mandatory**. A map of `key: value` labels used to select participating gateway instances.                                     |
| `name`         | **Optional**. Override the GatewayGroup name. Defaults to `metadata.name`.                                                      |
| `keepOnDelete` | **Optional**. When `true`, deleting the CR keeps the GatewayGroup in the control plane. Defaults to `false`.                    |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: GatewayGroup
metadata:
  name: prod-gateway-group
  namespace: default
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  labels:
    env: prod
    region: eu
    tier: gold
  keepOnDelete: true
```
