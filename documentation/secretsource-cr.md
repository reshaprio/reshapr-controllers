# SecretSource Custom Resource

## Overview

The `SecretSource` Custom Resource (CR) allows you to declare **Secrets** managed by the
reShapr control plane in a Kubernetes-native way. Secrets are the credentials attached to
[Services](./service-cr.md) or [Configuration Plans](./configurationplan-cr.md) — basic auth
tokens, bearer tokens, TLS certificates, OAuth2 client configurations, etc.

Each entry of a `SecretSource` can be either:

* **inlined** directly in the CR (for non-sensitive material or quick experiments), or
* **loaded from a Kubernetes `Secret`** at reconcile time via `valuesFrom.secretRef`, which is
  the recommended approach for GitOps workflows.

The `SecretSource` CRD is defined using the `reshapr.io/v1alpha1` API version. The full schema
definition is available in
[`secretsources.reshapr.io-v1.yml`](../deploy/crd/secretsources.reshapr.io-v1.yml).

At a higher level, a `SecretSource` resource is organized using the following structure:

```yaml
apiVersion: reshapr.io/v1alpha1
kind: SecretSource
metadata:
  name: tests-secrets
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  secrets:
    - <secret-specification-details>
    - <secret-specification-details>
  keepOnDelete: false
```

`spec.secrets` contains one or more Secret specification details.

The instance-targeting annotations (`reshapr.io/instance`, `reshapr.io/organization`) are
mandatory — see the [Instance connection flow](./instance-connection.md) for details.

Once created in your namespace, you can list existing SecretSources with:

```sh
$ kubectl get secretsources.reshapr.io -n my-ns
NAME            AGE
tests-secrets    1d
```

## Status structure

```yaml
apiVersion: reshapr.io/v1alpha1
kind: SecretSource
metadata:
  name: tests-secrets
spec:
  [...]
status:
  status: READY
  observedGeneration: 1
  conditions:
    - lastTransitionTime: "2026-08-26T12:34:56Z"
      status: READY
      type: github-token
      message: 66ca3b482a11675200f87792
    - lastTransitionTime: "2026-08-26T12:34:56Z"
      status: READY
      type: acme-basic-auth
      message: 66ca3b482a11675200f87793
```

Basically, one `condition` is created per entry of `spec.secrets` to track its individual
reconciliation result. The global outcome is exposed via `status.status`.

| Field                        | Description                                                                                                             |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `status.status`              | Global reconciliation status: `UNKNOWN`, `IN_PROGRESS`, `PREEXISTING`, `READY`, or `ERROR`.                             |
| `status.message`             | Human-readable message giving details about the current status.                                                         |
| `status.observedGeneration`  | The `metadata.generation` value at the time of the last successful reconciliation.                                       |
| `status.conditions[].type`   | Name of the Secret entry the condition relates to.                                                                       |
| `status.conditions[].status` | Per-Secret reconciliation status.                                                                                        |
| `status.conditions[].message`| Unique identifier of the corresponding Secret in the reShapr control plane (or an error message when status is `ERROR`). |

## SecretSource specification details

| Property       | Description                                                                                                                                     |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `secrets`      | **Mandatory**. List of [Secret specifications](#secret-specification) to synchronize with the reShapr control plane.                            |
| `keepOnDelete` | **Optional**. When `true`, deleting the CR keeps the Secrets in the control plane. Defaults to `false`.                                         |

### Secret specification

Each entry under `spec.secrets` follows this schema:

| Property                    | Description                                                                                                                                                                                     |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                      | **Mandatory**. Name of the Secret created in the reShapr target instance.                                                                                                                       |
| `description`               | **Mandatory**. Human-readable description of the Secret.                                                                                                                                        |
| `type`                      | **Optional**. Kind of Secret — one of `ENDPOINT` or `ARTIFACT`. Defaults to `ENDPOINT`.                                                                                                          |
| `username`                  | **Optional**. Username, for a Secret carrying basic authentication. Must be provided together with `password`.                                                                                  |
| `password`                  | **Optional**. Password, for a Secret carrying basic authentication.                                                                                                                             |
| `token`                     | **Optional**. Token, for a Secret carrying token-based authentication.                                                                                                                          |
| `tokenHeader`               | **Optional**. Header used to transport the token when calling the backend.                                                                                                                      |
| `certPem`                   | **Optional**. Certificate or certificate chain in PEM format, for a Secret carrying TLS material.                                                                                               |
| `useElicitation`            | **Optional**. When `true`, the Secret value is resolved through elicitation at runtime instead of being stored in the control plane.                                                            |
| `oauth2ClientConfiguration` | **Optional**. OAuth2 client configuration carried by this Secret — see [_OAuth2 client configuration_](#oauth2-client-configuration).                                                            |
| `valuesFrom`                | **Optional**. Reference to a Kubernetes `Secret` from which the values are loaded at reconcile time — see [_Loading values from a Kubernetes Secret_](#loading-values-from-a-kubernetes-secret). |

> [!WARNING]
> Do not store production credentials inlined in a `SecretSource` committed to Git. Prefer
> `valuesFrom.secretRef` and manage the underlying Kubernetes `Secret` with the tool of your
> choice (Sealed Secrets, External Secrets Operator, SOPS, ...).

### Loading values from a Kubernetes Secret

Every sensitive field of a Secret entry can be sourced from a Kubernetes `Secret` living in
the **same namespace** as the `SecretSource`. This is expressed through the `valuesFrom`
sub-object:

| Property                | Description                                                                                                                             |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `secretRef`             | **Mandatory**. Name of the source Kubernetes `Secret` in the same namespace as the `SecretSource`.                                       |
| `usernameKey`           | **Optional**. Key of the source Secret whose value must be used as `username`.                                                          |
| `passwordKey`           | **Optional**. Key of the source Secret whose value must be used as `password`.                                                          |
| `tokenKey`              | **Optional**. Key of the source Secret whose value must be used as `token`.                                                             |
| `tokenHeaderKey`        | **Optional**. Key of the source Secret whose value must be used as `tokenHeader`.                                                       |
| `certPemKey`            | **Optional**. Key of the source Secret whose value must be used as `certPem`.                                                           |
| `oauth2ClientSecretKey` | **Optional**. Key of the source Secret whose value must be injected as `oauth2ClientConfiguration.clientSecret`.                        |

> [!NOTE]
> Reading these Kubernetes `Secrets` requires the operator ServiceAccount to have the
> `get`/`list`/`watch` verbs on the `secrets` resource in the target namespaces. The default
> operator manifest ships a **dedicated `ClusterRole` and `ClusterRoleBinding`** for that
> purpose — see the [Installation guide](./installation.md#rbac-for-secretsource-secret-reads)
> for how to tighten or disable it.

### OAuth2 client configuration

The `oauth2ClientConfiguration` sub-object mirrors the OAuth2 client configuration schema of
the reShapr control plane:

| Property                | Description                                                            |
|-------------------------|------------------------------------------------------------------------|
| `clientId`              | OAuth2 client identifier.                                              |
| `clientSecret`          | OAuth2 client secret. Can be injected via `valuesFrom.oauth2ClientSecretKey`. |
| `authorizationEndpoint` | Authorization endpoint of the OAuth2 authorization server.             |
| `tokenEndpoint`         | Token exchange endpoint of the OAuth2 authorization server.            |

## Complete example

```yaml
apiVersion: reshapr.io/v1alpha1
kind: SecretSource
metadata:
  name: tests-secrets
  namespace: default
  annotations:
    reshapr.io/instance: reshapr-control-plane-ctrl.reshapr-system
    reshapr.io/organization: reshapr
spec:
  secrets:
    - name: github-token
      description: Token used to talk to the GitHub API
      type: ENDPOINT
      valuesFrom:
        secretRef: github-credentials
        tokenKey: token
        tokenHeaderKey: tokenHeader
    - name: acme-basic-auth
      description: Basic auth for the ACME backend
      type: ENDPOINT
      username: acme
      password: s3cr3t
```
