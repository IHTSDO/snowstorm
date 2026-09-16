# Using Docker

## Pre-requisites

A somewhat obvious point, but please make sure you have installed Docker. Between the two containers started by default, Elasticsearch and Snowstorm, 8Gb memory is used, so make sure that your installation of docker has the necessary memory allocated. The optional SNOMED CT Browser container needs a little more.

The [`docker-compose.yml`](../docker-compose.yml) starts Elasticsearch 9.5.2. If you are upgrading an existing Snowstorm docker deployment from Elasticsearch 8.x, read the [Elasticsearch 9 upgrade guide](elasticsearch9-upgrade.md) first — Elasticsearch 9 will not start against an 8.x data directory, so the existing volume must be migrated or removed.

## Docker Virtual Memory Limits

Due to default virtual memory limits set by operating systems which is now too low for Elasticsearch and the Elasticsearch container in this deployment will fail.

In Ubuntu 20.04 onwards, You will need to run the following command before running `docker-compose up` :

```bash
sudo sysctl -w vm.max_map_count=262144
```

When using Windows and WSL2, the following command should be run in Powershell:

```
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

. Equivalent commands can be found for other operating systems.

More information can be found here - https://www.elastic.co/guide/en/elasticsearch/reference/current/vm-max-map-count.html

## Building Docker images

See [Using Jib to build containers](using-jib-to-build-containers.md)


## Starting Snowstorm

The `snowstorm` service in [`docker-compose.yml`](../docker-compose.yml) is set to `build: .`, which builds the image from the [`Dockerfile`](../Dockerfile) in the project root. That Dockerfile copies the jar from `target`, so the jar must be built first:

```bash
mvn clean package
```

Then, from the project directory run:

```bash
docker compose up -d
```

This starts Snowstorm and Elasticsearch in separate containers. **You will then need to load a SNOMED CT release into Snowstorm**.

If you would rather pull the published image than build one, comment out the `build: .` line in [`docker-compose.yml`](../docker-compose.yml) and uncomment the line above it:

```yaml
    image: snomedinternational/snowstorm:latest
    #build: .
```

The `entrypoint` must be commented out at the same time. The published image is built by [Jib](using-jib-to-build-containers.md), which lays the application out as a classpath rather than the single `/app/snowstorm.jar` that the local `Dockerfile` produces, so the `entrypoint` would fail with `Unable to access jarfile /app/snowstorm.jar`. Jib's own entrypoint starts the application, and the JVM options move to `JAVA_TOOL_OPTIONS`:

```yaml
    #entrypoint: [
    #  "java",
    #  ...
    #]
    environment:
      - "JAVA_TOOL_OPTIONS=-Xms2g -Xmx4g --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED"
```

Note the `--add-opens=` form: `JAVA_TOOL_OPTIONS` is split on whitespace, so the space-separated form used in the `entrypoint` fails to start the JVM.

## Loading SNOMED CT

To get SNOMED CT into your new docker environment, you will need load a SNOMED CT release directly into the running docker container using the [loading SNOMED instructions](loading-snomed.md).

The [`docker-compose.yml`](../docker-compose.yml) creates a docker volume that will be re-used when the containers are rebuilt, so your data will not be lost unless the volume is deleted.

## Browsing SNOMED CT content

The [`docker-compose.yml`](../docker-compose.yml) also contains a [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) container, commented out by default. Uncomment the `browser:` section to start it alongside Snowstorm; once running it can be accessed simply on port 80 at http://localhost (or the URL/ip address of the host where the containers are running).

To run the browser from another server using docker instead, please refer to the instructions in the [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) repository.

## Running in Read-Only mode

Once the data is loaded Snowstorm can be run in read-only mode, as documented elsewhere. In order to run your docker containers in a read-only mode, add the flag to the `command` of the `snowstorm` service in [`docker-compose.yml`](../docker-compose.yml):

```yaml
    command: [
      "--elasticsearch.urls=http://es:9200",
      "--snowstorm.rest-api.readonly=true",
    ]
```

Other config options may be of interest when running you own instance, for example `snowstorm.rest-api.readonly.allowReadOnlyPostEndpoints` and `snowstorm.rest-api.allowUnlimitedConceptPagination` which default to false. See the [Configuration Guide](configuration-guide.md).
