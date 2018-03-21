# ❄️ Snowstorm Terminology Server

## (Not Production Ready)

Snowstorm is currently a proof of concept SNOMED CT terminology server built on top of Elasticsearch, with a focus on performance and enterprise scalability.


### Docker it
There is docker compose option which will run everything you need to use Snowstorm without the need to build anything.

The first time it's run, a SNOMED CT edition will need to be imported. Put the edition RF2 zip file in a relevant folder and change the location for the volumes in docker-compose-load.yml (replaces ~/releases with the absolute file location):
```
volumes:
  -  ~/releases:/opt
```

Then, run the import docker compose file (it will take a while to load SNOMED CT into the Elasticsearch container):
```
docker-compose up docker/docker-compose-load.yml up
```

All subsequent runs, use the other docker compose file otherwise you will be importing each time:

```
docker-compose up docker/docker-compose.yml up
```


## Documentation
Documentation is sparse for now, but will be improved as the project moves out of a proof of concept phase.

- [Getting Started](docs/getting-started.md)
- [Loading SNOMED](docs/loading-snomed.md)
- [Using the API](docs/using-the-api.md)
- [Configuration Guide](docs/configuration-guide.md)
