# Operator Installation

This page describes how to install the reShapr Operator components on a Kubernetes cluster.

## Prerequisites

* A Kubernetes cluster (1.27+) with cluster-admin privileges,
* [`kubectl`](https://kubernetes.io/docs/tasks/tools/) configured to talk to that cluster,
* A running **reShapr control plane** reachable from within the cluster,

## 1. Install the Custom Resource Definitions

The four CRDs shipped by this repository are located under [`deploy/crd/`](../deploy/crd/):

```sh
kubectl apply -f deploy/crd/services.reshapr.io-v1.yml
kubectl apply -f deploy/crd/gatewaygroups.reshapr.io-v1.yml
kubectl apply -f deploy/crd/configurationplans.reshapr.io-v1.yml
kubectl apply -f deploy/crd/customtools.reshapr.io-v1.yml
kubectl apply -f deploy/crd/expositions.reshapr.io-v1.yml
kubectl apply -f deploy/crd/secretsources.reshapr.io-v1.yml
```

You can verify the installation with:

```sh
$ kubectl get crd | grep reshapr.io
configurationplans.reshapr.io   2025-08-25T09:12:03Z
customtools.reshapr.io          2025-08-25T09:12:03Z
expositions.reshapr.io          2025-08-25T09:12:03Z
gatewaygroups.reshapr.io        2025-08-25T09:12:03Z
secretsources.reshapr.io        2025-08-25T09:12:03Z
services.reshapr.io             2025-08-25T09:12:03Z
```

## 2. Install the Operator

The operator manifests are located in [`deploy/operator.yaml`](../deploy/operator.yaml) and
install everything in the `reshapr-system` namespace:

```sh
kubectl apply -f deploy/operator.yaml
```

This creates:

* The `reshapr-system` namespace,
* A `ServiceAccount` named `reshapr-operator`,
* A `Deployment` running the operator container
  (`quay.io/lbroudoux/reshapr-operator:nightly`),
* Two `ClusterRole` / `ClusterRoleBinding` pairs granting the required RBAC — see the
  [RBAC section](#rbac-for-secretsource-secret-reads) below.

Verify that the operator Pod becomes ready:

```sh
kubectl -n reshapr-system get pods -l name=reshapr-operator
```

### RBAC for SecretSource Secret reads

The operator manifest ships **two separate `ClusterRole` / `ClusterRoleBinding` pairs** rather
than a single monolithic role, in order to make cluster-wide permissions easier to audit and
selectively disable:

| ClusterRole                          | ClusterRoleBinding                     | Purpose                                                                                                                                                              |
|--------------------------------------|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `reshapr-operator`                   | `reshapr-operator`                     | Full access to the `reshapr.io` API group — required for the operator to reconcile every reShapr CR.                                                                  |
| `reshapr-operator-secret-reader`     | `reshapr-operator-secret-reader`       | `get` / `list` / `watch` on core `secrets` — used **only** by the [`SecretSource`](./secretsource-cr.md) reconciler to resolve `spec.secrets[].valuesFrom.secretRef`. |

Splitting the two roles has two practical benefits:

1. **Auditability** — a security review can immediately spot that the operator needs
   cluster-wide access to Kubernetes `Secrets` and reason about it independently from the rest
   of the RBAC.
2. **Scoping or disabling the SecretSource feature** — if you do not intend to use
   `SecretSource` resources (or want to restrict `Secret` reads to a subset of namespaces),
   you can safely tune the second binding without impacting the rest of the operator.

Common variants:

* **Disable the `SecretSource` feature entirely** — delete only the secret-reader binding
  (the operator will still start, but SecretSource reconciliations will end in `ERROR`
  whenever `valuesFrom` is used):

  ```sh
  kubectl delete clusterrolebinding reshapr-operator-secret-reader
  ```

* **Restrict `Secret` reads to specific namespaces** — replace the cluster-wide binding by one
  or several namespaced `RoleBinding`s referencing the `reshapr-operator-secret-reader`
  `ClusterRole`:

  ```yaml
  apiVersion: rbac.authorization.k8s.io/v1
  kind: RoleBinding
  metadata:
    name: reshapr-operator-secret-reader
    namespace: my-team
  subjects:
    - kind: ServiceAccount
      name: reshapr-operator
      namespace: reshapr-system
  roleRef:
    kind: ClusterRole
    name: reshapr-operator-secret-reader
    apiGroup: rbac.authorization.k8s.io
  ```

  ```sh
  kubectl delete clusterrolebinding reshapr-operator-secret-reader
  kubectl apply -f my-team-secret-reader-binding.yaml
  ```

## 3. Prepare the Reshapr Control Plane Connection

Operator will run the [Instance connection flow](./instance-connection.md) during the reconciliation process.
For that, it needs to be able to reach and authenticate to the Reshapr control plane. On the control plane side, 
the Service Account `reshapr-operator` needs to be able to declared as a trusted client. 

This is done by declaring a `ServiceAccount` entity with the list of organizations it will be allowed to impersonate.
You can either use the Reshapr CLI `admin` command or the Admin API to do that.

1. Using the Reshapr CLI, run the following command:

```sh
export RESHAPR_ADMIN_API_KEY='<admin-api-key>'

reshapr admin --server https://reshapr.acme.loc service-account create reshapr-system-operator \
  --k8s-subject reshapr-system:reshapr-operator \
  --allowed-organizations '["*"]' \
  --validity-days 90
```

This declares the `reshapr-system:reshapr-operator` Kubernetes Service Account as a trusted client for
the Control Plane authentication. Also, the `reshapr-system-operator` Service Account will be able to 
impersonate any organization (`--allowed-organizations` flag used).

2. Using the Admin API, you can also declare the Service Account as a trusted client by sending a 
POST request to the `api//admin/serviceAccounts` endpoint like this:

```shell
curl -XPOST $SERVER_URL/api/admin/serviceAccounts -H "Content-Type: application/json" \ -H "x-reshapr-api-key: $SERVER_TOKEN" \
  -d '{"name":"reshapr-system-operator", "k8sSubject":"reshapr-system:reshapr-operator", "allowedOrganizations":["*"], "validityDays":90}'
```

## 4. Deploy your first resources

Sample manifests are provided in [`deploy/samples/`](../deploy/samples/). For example:

```sh
kubectl apply -f deploy/samples/open-meteo-api-service.yaml
kubectl apply -f deploy/samples/qa-gateway-group.yaml
```

Then check the reconciliation status:

```sh
kubectl get services.reshapr.io -A
kubectl get gatewaygroups.reshapr.io -A
```

## Uninstall

To fully uninstall the controllers:

```sh
kubectl delete -f admission/k8s/mutating-webhook-configuration.yml
kubectl delete -f admission/k8s/admission-controller.yaml
kubectl delete -f admission/k8s/cert-manager-resources.yaml
kubectl delete -f deploy/operator.yaml
kubectl delete -f deploy/crd/
```

> [!WARNING]
> Deleting the CRDs removes every custom resource of these kinds across all namespaces. Make
> sure this is what you intend to do.
