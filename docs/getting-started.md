# Getting Started

## Prerequisites

- Java 11
- Maven 3
- [SNOMED CT International Edition RF2 release files](https://www.snomed.org/snomed-ct/get-snomed)
- About 8G of memory

## More on Memory Requirements

As a minimum Snowstorm should have 2G and Elasticsearch should have 4G of memory in order to import a Snapshot and perform ECL queries. 
Elasticsearch will work best with another 4G of memory left free on the server for OS level disk caching. 

## Setup
### Install Elasticsearch
Download and install [Elasticsearch version 7](https://www.elastic.co/downloads/past-releases/elasticsearch-7-10-0) (tested against 7.10.0). 

Update the configuration file _config/jvm.options_ with the memory options `-Xms4g` and `-Xmx4g`.

### Get Snowstorm Application Jar
Download the latest release jar from the [releases page](https://github.com/IHTSDO/snowstorm/releases).

#### _Alternatively_ if you would like to build Snowstorm from source code:
  - [Docker](https://docs.docker.com/get-docker) must be running because unit tests use the [TestContainers framework](https://www.testcontainers.org/supported_docker_environment). The latest version of Docker is recommended.
  - Then use maven:
    ```bash
    mvn clean package
    ```
  - Maven creates the jar file in the 'target' directory.

## Start Snowstorm

First start Elasticsearch from wherever it has been installed.
```bash
./bin/elasticsearch
```

On the first run of Snowstorm the SNOMED CT data may need to be loaded. [Follow instructions here](loading-snomed.md).

On subsequent runs just start Snowstorm (in read only mode).
```bash
java -Xms2g -Xmx4g -jar target/snowstorm*.jar --snowstorm.rest-api.readonly=true
```
