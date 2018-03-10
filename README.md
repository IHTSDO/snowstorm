# ❄️ Snowstorm Terminology Server

## (Not Production Ready)

SNOMED CT Authoring server built on Elasticsearch.

## Development Setup

### Install Elasticsearch
  - Download and unzip [Elasticsearch **6.0.1**](https://www.elastic.co/downloads/past-releases/elasticsearch-6-0-1) (Must be this version)
  - Update _config/jvm.options_ with `-Xms4g` and `-Xmx4g`.
  - Start with _./bin/elasticsearch_

### Run Snowstorm
Once Elasticsearch is running build and run Snowstorm:
```
mvn clean install
java -jar target/snowstorm*.jar
```

## Documentation
Documentation is appearing, slowly but surely, and can be found in the [docs folder](docs/introduction.md)
