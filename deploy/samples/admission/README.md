## Deploying the API Pastries admission controller demo

* Deploy a Reshapr control-plane into `reshapr-system` Kubernetes namespace
* On this Reshapr instance, create an organization `pastries` with an owner (ex: `pastries-admin`)
* Adjust the quoptas of this organization so that it can create Gateway Groups and Gateways
* On this organization:
  * create a `pastries-token` Gateway API Token. Copy the token value (ex: `MVIS-IJ9vkoZvP6q-p1FQ73DVaK3iZC8g2wWq8WTS90`)

* Create a `pastries` Kubernetes namespace

* You can now deploy Reshapr entities for this organization (if you also have the operator installed) or create them by hand...

```sh
kubectl apply -f apipastries-reshapr-entities.yaml -n pastries
```

* Create a `reshapr-cluster.jceks` keystore holding the keys used for Reshapr proxies in-cluster sync

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
  -nopromptAdmi
```

* Import eveything as a secret within the `pastries` Kubernetes namespace

```sh
NAMESPACE=pastries
API_TOKEN=MVIS-IJ9vkoZvP6q-p1FQ73DVaK3iZC8g2wWq8WTS90

kubectl create secret generic reshapr-proxy-config \
  --namespace "$NAMESPACE" \
  --from-file=reshapr-cluster.jceks=./reshapr-cluster.jceks \
  --from-literal=cluster-store-password="$STORE_PASSWORD" \
  --from-literal=cluster-key-password="$KEY_PASSWORD" \
  --from-literal=control-plane-host=reshapr-control-plane-ctrl.reshapr-system \
  --from-literal=control-plane-port=5555 \
  --from-literal=control-plane-tls-plaintext=true \
  --from-literal=control-plane-token="pastries-$API_TOKEN" \
  --from-literal=gateway-labels='env=dev;team=pastries'
```

* Now deploy the `apipastries` application with annotated Kubernetes deployment to have auto-injection of proxy:

```sh
kubectl apply -f apipastries-03-deployment.yaml -n pastries
```