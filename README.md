# ❄️ Snowstorm Terminology Server

## (Not Production Ready)

Snowstorm is currently a proof of concept SNOMED CT terminology server built on top of Elasticsearch, with a focus on performance and enterprise scalability.

### Docker it
There is docker compose option which will run everything you need to use Snowstorm without the need to build anything. However, you will need the pre-generated elasticsearch indexes which you can either generate yourself, see the [snomed loading instructions here](docs/loading-snomed.md), or contact [techsupport@snomed.org](mailto::techsupport@snomed.org) to get a copy of the indexes.

To build the docker file (whilst it has not yet been uploaded to Docker Hub)
```
mvn clean install dockerfile:build
```

All subsequent runs, use the other docker compose file otherwise you will be importing each time:
```
mvn clean install dockerfile:build
docker-compose up -d
```


## Documentation
Documentation is sparse for now, but will be improved as the project moves out of a proof of concept phase.

- [Getting Started](docs/getting-started.md)
- [Loading SNOMED](docs/loading-snomed.md)
- [Using the API](docs/using-the-api.md)
- [Configuration Guide](docs/configuration-guide.md)
