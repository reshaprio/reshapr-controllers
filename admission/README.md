# Package the admission controller

```bash
mvn clean package
```

# Build your admission controller image

```bash
docker build -f src/main/docker/Dockerfile.jvm -t quay.io/lbroudoux/reshapr-admission-controller:nightly . \
  && docker push quay.io/lbroudoux/reshapr-admission-controller:nightly
```