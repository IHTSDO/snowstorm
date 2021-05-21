# Using Docker

## Pre-requisites

A somewhat obvious point, but please make sure you have installed Docker. Between the three containers, Elasticsearch, Snowstorm and the SNOMED CT Browser, 8Gb memory is used, so make sure that your installation of docker has the necessary memory allocated.

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

## Starting Snowstorm

From the project directory run:

```bash
docker-compose up -d
```

This uses the [`docker-compose.yml`](../docker-compose.yml) file and will start Snowstorm,  Elasticsearch and the [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) in separate containers without the need to build anything. However, **you will need to load a SNOMED CT release into Snowstorm**.

## Loading SNOMED CT

To get SNOMED CT into your new docker environment, you will need load a SNOMED CT release directly into the running docker container using the [loading SNOMED instructions](loading-snomed.md).

The [`docker-compose.yml`](../docker-compose.yml) creates a docker volume that will be re-used when the containers are rebuilt, so your data will not be lost unless the volume is deleted.

## Browsing SNOMED CT content

The [`docker-compose.yml`](../docker-compose.yml) also includes a [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) container which, once running, can be accessed simply on port 80 at http://localhost (or the the URL/ip address of the host where the containers are running).

However, if you do not want to run the browser on the same instance, then remove the relevant `browser:` section in [`docker-compose.yml`](../docker-compose.yml). To run the browser from another server using docker, please refer to the instructions in the [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) repository.

## Running in Read-Only mode

Once the data is loaded Snowstorm can be run in read-only mode, as documented elsewhere. In order to run your docker containers in a read-only mode, please make the following change to the relevant line in the [`docker-compose.yml`](../docker-compose.yml) file:

```bash
entrypoint: java -Xms2g -Xmx4g -jar snowstorm.jar --elasticsearch.urls=http://es:9200 --snowstorm.rest-api.readonly=true
```

Other config options may be of interest when running you own instance, for example `snowstorm.rest-api.readonly.allowReadOnlyPostEndpoints` and `snowstorm.rest-api.allowUnlimitedConceptPagination` which default to false. See the [Configuration Guide](configuration-guide.md).