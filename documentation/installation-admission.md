# Admission Webhook Installation

This page describes how to install the reShapr Controllers components on a Kubernetes cluster.

## Prerequisites

* A Kubernetes cluster (1.27+) with cluster-admin privileges,
* [`kubectl`](https://kubernetes.io/docs/tasks/tools/) configured to talk to that cluster,
* A running **reShapr control plane** reachable from within the cluster,
* [cert-manager](https://cert-manager.io/) installed — required by the admission webhook to
  provision its serving TLS certificate.

## Install the Admission Webhook

The admission controller manifests live under [`admission/k8s/`](../admission/k8s/) and rely on
cert-manager to provision the TLS material required by the mutating webhook.

```sh
kubectl apply -f admission/k8s/cert-manager-resources.yaml
kubectl apply -f admission/k8s/admission-controller.yaml
kubectl apply -f admission/k8s/mutating-webhook-configuration.yml
```

Verify the webhook Pod becomes ready and that the `MutatingWebhookConfiguration` is registered:

```sh
kubectl -n reshapr-system get pods -l app.kubernetes.io/name=reshapr-admission-controller
kubectl get mutatingwebhookconfigurations mutating.reshapr.io
```

See the [Admission controller documentation](./admission-controller.md) for further details.
