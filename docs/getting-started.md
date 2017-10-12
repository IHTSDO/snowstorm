# Getting Started

## Pre-requisites

- Elasticsearch **v2.4.4** (specific version expected)
- Maven for building project (if you didn't know that one already!)
- SNOMED CT International release files, [more information here](http://www.snomed.org)
- At least **8gb** RAM to spare to run Elasticsearch

## Recommended ENV settings

Depending on the memory available to you, the following is advised:

- Snowstorm 20%
- Elasticsearch 40%
- Leave free for OS disk caching 40%

## Building...

```
git clone https://github.com/IHTSDO/elastic-snomed.git
cd elastic-snomed
mvn clean package
```

## Getting go...

- start Elasticsearch from wherever it has been installed (ensuring the heap size has been correctly assigned)
- and then [starting Snowstorm to load in data](loading-snomed.md)

Then sit back and watch the magic happen as it loads
