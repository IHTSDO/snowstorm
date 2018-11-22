# Using Docker

Please be aware this is in development and, currently, the easiest way to use snowstorm is to download the most recent jar from [this GitHub repo](https://github.com/IHTSDO/snowstorm/releases).

However, this is another option because it will install and run the correct Elasticsearch version without the need to install and configure other software as well as running Snowstorm.

The docker-compose.yml in the repo option will run everything necessary to use Snowstorm without the need to build anything. However, **you will need to load SNOMED CT** so make sure you read the [SNOMED loading instructions here](docs/loading-snomed.md).

You can change the location that the Elasticserach indices are created on the host machine by changing the following line in [docker-compose.yml](docker-compose.yml) from ~/elastic to a local folder of your choice:

```yml
    volumes:
      - ~/elastic:/usr/share/elasticsearch/data
```

Once done, then simply run:

```bash
docker-compose up -d
```