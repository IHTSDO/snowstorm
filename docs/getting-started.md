# Getting Started

## Prerequisites

- Java 8
- Maven 3
- [SNOMED CT International Edition RF2 release files](https://www.snomed.org/snomed-ct/get-snomed)
- About 8G of memory

## More on Memory Requirements

As a minimum Snowstorm should have 2G and Elasticsearch should have 4G of memory in order to import a Snapshot and perform ECL queries. 
Elasticsearch will work best with another 4G of memory left free on the server for OS level disk caching. 

## Setup
### Install Elasticsearch
Download and install [Elasticsearch **6.x**](https://www.elastic.co/downloads/past-releases/elasticsearch-6-5-4) (tested against 6.5.4).

Update the configuration file _config/jvm.options_ with the memory options `-Xms4g` and `-Xmx4g`.

### Build Snowstorm
Build Snowstorm using maven:
```bash
mvn clean package
```

**Or** download the most latest released jar from [the repository releases](https://github.com/IHTSDO/snowstorm/releases).


## Start Snowstorm

First start Elasticsearch from wherever it has been installed.
```bash
./bin/elasticsearch
```

On the first run of Snowstorm the SNOMED CT data may need to be loaded. [Follow instructions here](loading-snomed.md).

On subsequent runs just start Snowstorm (in read only mode).
```bash
java -Xms2g -Xmx2g -jar target/snowstorm*.jar --snowstorm.rest-api.readonly=true
```
