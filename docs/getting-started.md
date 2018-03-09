# Getting Started

## Pre-requisites

- Java 8
- Maven3
- [Elasticsearch v6.0.1](https://www.elastic.co/downloads/past-releases/elasticsearch-6-0-1) (A later minor version should work)
- SNOMED CT International release files, [more information here](http://www.snomed.org)
- At least **8gb** RAM to spare to run Elasticsearch

## Recommended ENV settings

Depending on the memory available to you, the following is advised:

- Snowstorm 20%
- Elasticsearch 40%
- Leave free for OS disk caching 40%

## Building...

```
git clone https://github.com/IHTSDO/snowstorm.git
cd snowstorm
mvn clean package
```

## Getting go...

- start Elasticsearch from wherever it has been installed (ensuring the heap size has been correctly assigned)
- if you have not imported any data, [start Snowstorm to load in data](loading-snomed.md)
- otherwise, run using `java -Xms5g -Xmx5g -jar target/snowstorm-<version>.jar`

Then sit back and watch the magic happen as it loads
