# Using Docker

Please be aware this is in development and, currently, the easiest way to use snowstorm is to download the most recent jar from [this GitHub repo](https://github.com/IHTSDO/snowstorm/releases).

However, this is another option because it will install and run the correct Elasticsearch version without the need to install and configure other software as well as running Snowstorm.

This is an easy option because it will install correct Elasticsearch version without the need to install and configure other software.

The docker-compose.yml in the repo option will run everything necessary to use Snowstorm without the need to build anything. However, **you will need to generate SNOMED CT elasticsearch indices** which you can generate, see the [snomed loading instructions here](docs/loading-snomed.md).

Once you have the indices, you can either unzip them into a local ~/elastic folder or change the following line in [docker-compose.yml](docker-compose.yml) from ~/elastic to a local folder of your choice:

```yml
    volumes:
      - ~/elastic:/usr/share/elasticsearch/data
```

Once done, then simply run:

```bash
docker-compose up -d
java -Xms2g -Xmx2g -jar target/snowstorm*.jar --snowstorm.rest-api.readonly=true
```
