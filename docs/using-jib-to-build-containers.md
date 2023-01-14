# Using Jib to build containers

## Introduction

This project uses [Google Jib](https://github.com/GoogleContainerTools/jib) to build optimised Docker/OCI images, without the need for a Dockerfile and Docker Engine.

The Maven plugin supports the definition of the container in `pom.xml` and provides new goals to build a container. The full documentation for the Maven Plugin can be found [here](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin).


## Multiple Platform Support

Jib supports building container images for multiple platform architectures e.g. `linux/amd64`, `linux/arm64`.

Note: in order to achieve this the base image must support the desired platforms.

### Amazon Corretto

The [Amazon Corretto](https://hub.docker.com/_/amazoncorretto) OpenJDK 11 base image supports both `linux/amd64` and `linux/arm64`. 


## Build to remote container registry

Jib supports build and pushing of images to a cloud container registry including:

- DockerHub
- Google Container Registry (GCR)
- AWS Elastic Container Registry (ECR)
- Azure Container Registry (ACR)
- JFrog Container Registry (JCR)

The following command can be used to build and push images to a registry.

```
mvn compile jib:build
```

The configuration of the destination registry and authentication methods are documented here:

- [Registry](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#configuration)
- [Authentication](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#authentication-methods)

## Build to a local docker registry

The following steps describe how to build to a local registry

1. Launch a local docker registry

```
docker run -d -p 5001:5000 --name registry registry:2
```

2. Set `docker.registry` property in pom.xml

```
<docker.registry>localhost:5001/</docker.registry>
```

3. Set `docker.allowInsecureRegistries` property in `pom.xml`

```
<docker.allowInsecureRegistries>true<docker.allowInsecureRegistries>
```

4. Run the build

```
mvn compile jib:build
```


## Build to a local docker daemon

Jib supports building an image directly to a local docker daemon. However it is not possible to build for multiple platforms with this approach.

In order to build an image for a specific platform (either linux/amd64 or linux/arm64) two maven profiles have been configured:

### Profile: docker-amd64

```
mvn compile jib:dockerBuild -P docker-amd64
```

### Profile: docker-arm64

```
mvn compile jib:dockerBuild -P docker-arm64
```
