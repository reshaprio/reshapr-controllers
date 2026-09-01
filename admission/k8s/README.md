# reShapr proxy configuration Secret

The admission controller injects a `reshapr-proxy` sidecar into every Pod annotated with
`io.reshapr/inject: "true"`. Values that cannot be derived from the Pod are read from a
namespace-local Secret named **`reshapr-proxy-config`** (override with the
`io.reshapr/config-secret-name` annotation).

This Secret must be pre-deployed in **the same namespace as the annotated workloads** — it is a
prerequisite for the sidecar to start. A future Helm chart will automate its creation (including the
keystore generation); until then, create it manually as described below.

> The [`proxy-config-secret.yaml`](./proxy-config-secret.yaml) manifest is a template. Prefer the
> imperative `kubectl` approach below, which assembles the string values and the binary keystore in
> a single Secret.

## Secret keys

| Key                           | Injected env var                 | Sensitive | Notes                                                        |
|-------------------------------|----------------------------------|:---------:|-------------------------------------------------------------|
| `control-plane-host`          | `RESHAPR_CTRL_HOST`              |    no     | Control plane gRPC host                                      |
| `control-plane-port`          | `RESHAPR_CTRL_PORT`             |    no     | Control plane gRPC port                                      |
| `control-plane-tls-plaintext` | `RESHAPR_CTRL_TLS_PLAINTEXT`     |    no     | `true` for plaintext gRPC (not recommended in production)    |
| `control-plane-token`         | `RESHAPR_CTRL_TOKEN`             |  **yes**  | Gateway token issued by the control plane                   |
| `gateway-fqdns`               | `RESHAPR_GATEWAY_FQDNS`         |    no     | Comma-separated advertised FQDNs                            |
| `gateway-labels`              | `RESHAPR_GATEWAY_LABELS`         |    no     | `;`-separated `key=value` labels for GatewayGroup selection  |
| `cluster-store-password`      | `RESHAPR_CLUSTER_STORE_PASSWORD` |  **yes**  | JCEKS keystore store password                               |
| `cluster-key-password`        | `RESHAPR_CLUSTER_KEY_PASSWORD`   |  **yes**  | JCEKS keystore key password                                 |
| `reshapr-cluster.jceks`       | mounted at `/etc/reshapr/keystore` |  **yes**  | JGroups SYM_ENCRYPT keystore (binary)                     |

The non-sensitive keys can be overridden per workload through annotations (e.g.
`io.reshapr/control-plane-host`); the sensitive keys are always read from the Secret via
`secretKeyRef`.

## 1. Generate the cluster keystore

The proxy sidecars encrypt their JGroups traffic with a shared **AES-256** symmetric key stored in a
JCEKS keystore. All sidecars that must form the same cluster have to share the **same** keystore and
passwords. Generate it once with `keytool` (bundled with any JDK):

```sh
# Pick strong random passwords (store them safely — they go into the Secret below).
STORE_PASSWORD=$(head -c 24 /dev/urandom | base64 | tr -d '\n')
KEY_PASSWORD=$(head -c 24 /dev/urandom | base64 | tr -d '\n')

keytool -genseckey \
  -alias reshapr-cluster \
  -keyalg AES -keysize 256 \
  -storetype JCEKS \
  -keystore reshapr-cluster.jceks \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -noprompt
```

> The alias **must** be `reshapr-cluster` and the file **must** be named `reshapr-cluster.jceks`,
> matching the defaults used by the injected sidecar.

## 2. Create the Secret

```sh
NAMESPACE=<your-workload-namespace>

kubectl create secret generic reshapr-proxy-config \
  --namespace "$NAMESPACE" \
  --from-file=reshapr-cluster.jceks=./reshapr-cluster.jceks \
  --from-literal=cluster-store-password="$STORE_PASSWORD" \
  --from-literal=cluster-key-password="$KEY_PASSWORD" \
  --from-literal=control-plane-host=reshapr-control-plane-ctrl.reshapr-system \
  --from-literal=control-plane-port=5555 \
  --from-literal=control-plane-tls-plaintext=true \
  --from-literal=control-plane-token=<gateway-token> \
  --from-literal=gateway-labels='env=prod;team=reshapr'
```

## 3. Annotate the workload

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  template:
    metadata:
      annotations:
        io.reshapr/inject: "true"
```

The admission controller then injects the sidecar and its `DeploymentProxyReconciler` provisions the
headless Service `reshapr-proxy-my-app` used for cluster discovery.

## Sharing one keystore across namespaces

The Secret is namespace-scoped, so deploy a copy in every namespace that hosts injected workloads.
To let sidecars in different namespaces join the **same** cluster, reuse the **same**
`reshapr-cluster.jceks` file and passwords when creating each Secret.
