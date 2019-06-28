# Elasticsearch Index Mapping Changes

### Intro
Snowstorm uses Elasticsearch indexes to store data. 
Each index has a mapping which is a list of fields and types, like a database table schema. 

Sometimes a Snowstorm release will include a change to the mapping of one or more of the indices. This is usually to support some new feature or enhancement and in most cases you won't be required to do anything (See below). 

### When Possible
- **Minor releases** will only contain mapping changes which are backward compatible with the previous version.
  The mapping change will be clearly marked in the release notes.
  Updating the mapping will not be necessary _unless_ access to the new feature mentioned in the release notes, which requires the mapping change, is required.
- **Major releases** _may_ contain mapping changes which are not backward compatible with the previous version.
  These will be clearly marked in a "Breaking" section of the release notes and include details.

### Reindexing
For some types of mapping changes an existing Snowstorm index can be migrated to the new format using the Elasticseaech reindexing process while preserving the data. The release notes will indicate when this procedure can be used to update an index mapping.

In this case we need to take a copy of the index, delete the old one then recreate it. These are the steps and Elasticsearch commands _(see also Running Elasticsearch Commands below)_.

- Copy the index and all its data to a new temp index.
  - Example using the `description` index:
```
POST _reindex {"source": {"index": "description"}, "dest": {"index": "description_temp"}}
```
- Stop Snowstorm.
- Delete the old index.
  - Example deleting the `description` index:
```
DELETE /description
```
- Start Snowstorm. This will recreate an empty index with the latest mapping.
- Copy the data from the temp index into the new index.
```
POST _reindex {"source": {"index": "description_temp"}, "dest": {"index": "description"}}
```
- Reindex is complete for that index.


### Running Elasticsearch Commands
Elasticsearch commands can be run in a Kibana Dev console or converted to a curl statement like this:
```bash
# Format:
curl -X{METHOD} -H 'Content-Type:application/json' localhost:9200/{RELATIVE_URL} --data '{BODY}'
# Example
curl -XPOST -H 'Content-Type:application/json' localhost:9200/_reindex --data '{"source": {"index": "description_temp"}, "dest": {"index": "description"}}'
```
