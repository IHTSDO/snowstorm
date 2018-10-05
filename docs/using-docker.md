# Using Docker

## Experimental (definitely not yet ready for production)

Please be aware this is experimental and, currently, the easiest way to use snowstorm is to download the most recent jar from [this GitHub repo](https://github.com/IHTSDO/snowstorm/releases).

This is an easy option because it will install Snowstorm, the correct Elasticsearch version and wire them together for you.

The docker-compose.yml in the repo option will run everything necessary to use Snowstorm without the need to build anything. However, **you will need to generate SNOMED CT elasticsearch indices** which you can either generate yourself, see the [snomed loading instructions here](docs/loading-snomed.md), or contact [techsupport@snomed.org](mailto::techsupport@snomed.org) to get access to a copy of some already generated indices.

Once you have the indices, you can either unzip them into a local ~/elastic folder or change the following line in [docker-compose.yml](docker-compose.yml) from ~/elastic to a local folder of your choice:

```yml

    volumes:
      - ~/elastic:/usr/share/elasticsearch/data

```

Once done, then simply run:

```bash

docker-compose up -d

```
