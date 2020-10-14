# Using Docker


## Starting Snowstorm

From the project directory run:
```
docker-compose up
```

This uses the `docker-compose.yml` file and will start Snowstorm and Elasticsearch in separate containers without the need to build anything. However, **you will need to load a SNOMED CT release into Snowstorm**.


## Loading SNOMED CT

You have two options for getting data into your docker environment. The first option is to load a SNOMED CT release directly into the running docker container using the [loading SNOMED instructions](loading-snomed.md). This is simple but requires a docker container with more memory during the loading process and the data will be destroyed when the container is destroyed. 

An alternative is to load a SNOMED CT release into Snowstorm in a different environment, using the standalone jar for example, and then make the built Elasticsearch indicies available to docker. Once the release loading is complete shut down Snowstorm and the Elasticsearch container then the indices under elasticsearch/data can be copied. They could either be copied into the docker ~/elastic folder or change the following line in [docker-compose.yml](docker-compose.yml) from ~/elastic to a local folder of your choice:

```yml
    volumes:
      - ~/elastic:/usr/share/elasticsearch/data
```


## Running in Read-Only mode

Once the data is loaded Snowstorm can be run in read-only mode like so:
```bash
docker-compose up -d
java -Xms2g -Xmx2g -jar target/snowstorm*.jar --snowstorm.rest-api.readonly=true
```
Other config options may be of interest when running you own instance, for example `snowstorm.rest-api.readonly.allowReadOnlyPostEndpoints` and `snowstorm.rest-api.allowUnlimitedConceptPagination` which default to false. See the [Configuration Guide](configuration-guide.md).
