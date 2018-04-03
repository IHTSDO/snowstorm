# ❄️ Snowstorm Terminology Server

## (Not Production Ready)

Snowstorm is currently a proof of concept SNOMED CT terminology server built on top of Elasticsearch, with a focus on performance and enterprise scalability.


## Documentation
Documentation is sparse for now, but will be improved as the project moves out of a proof of concept phase.

- [Getting Started](docs/getting-started.md)
- [Using Docker image](docs/using-docker.md)
- [Loading SNOMED](docs/loading-snomed.md)
- [Using the API](docs/using-the-api.md)
- [Configuration Guide](docs/configuration-guide.md)

### Docker (quick start)

It is strongly recommended to use docker compose, instead of the snowstorm container on its own which will run everything necessary to use Snowstorm without the need to build anything.

However, **you will need to generate SNOMED CT elasticsearch indices** which you can either generate yourself, see the [snomed loading instructions here](docs/loading-snomed.md), or contact [techsupport@snomed.org](mailto::techsupport@snomed.org) to get access to a copy of the already generated indices.

Once you have the indices, you can either unzip them into a local ~/elastic folder or change the following line in [docker-compose.yml](docker-compose.yml) from ~/elastic to a local folder of your choice:
```    
    volumes:
      - ~/elastic:/usr/share/elasticsearch/data
```
Once done, then simply run:
```
docker-compose up -d
```
