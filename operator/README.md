# Package the operator

```bash
mvn clean package
```

# Build your operator image

```bash
docker build -f src/main/docker/Dockerfile.jvm -t quay.io/lbroudoux/reshapr-operator:nightly . \
  && docker push quay.io/lbroudoux/reshapr-operator:nightly  
```