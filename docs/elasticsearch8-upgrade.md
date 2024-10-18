# Elasticsearch 8 upgrade and data migration guide

Follow these steps below to migrate Elasticsearch 7.x to version 8.x if you need to keep data from an existing cluster. When running Snowstorm in a production environment, it is recommended to back up the data before starting the upgrade process.

You can skip this guide if you are trying out snowstorm for the first time as you can start with a fresh installation of Elasticsearch 8.x (8.11.1 onwards).

If you have run Snowstorm on version 9.2.0 or earlier in a docker environment and don't want to keep existing data you can skip this guide. However, you MUST remove the volume specified in [docker-compose.yml](../docker-compose.yml) file before starting snowstorm.

## Step one - Existing codesystem-version data migration

Existing data in the codesystem-version index may have the "importDate" field stored as date string or long. It has worked with Elasticsearch 7.x however these mixed values don't seem to work when upgrading to Elasticsearch 8.x.
For consistency, we have decided to change the field type to Long. "importDate" field is used to store the date when the version is imported into Snowstorm. It is not used for any other purpose.
If you don't need to keep this information as history, you can use Option 1. Otherwise, you can use Option 2.

### Option 1 - Remove importDate in codesystem-version index
```
POST codesystem-version/_update_by_query
{
  "script": "ctx._source.remove('importDate')",
  "query": {
    "bool": {
      "must": [
        {
          "exists": {
            "field": "importDate"
          }
        }
      ]
    }
  }
}
```

### Option 2 - Reindex codesystem-version and branch-review to change the field type of importDate and lastUpdated to long

#### In Kibana create a new index for codesystem-version-tmp with updated mapping
```
PUT codesystem-version-tmp
{
  "mappings": {
      "properties": {
        "branchPath": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "description": {
          "type": "keyword"
        },
        "effectiveDate": {
          "type": "integer"
        },
        "id": {
          "type": "keyword"
        },
        "importDate": {
          "type": "long"
        },
        "internalRelease": {
          "type": "boolean"
        },
        "parentBranchPath": {
          "type": "keyword"
        },
        "releasePackage": {
          "type": "keyword"
        },
        "shortName": {
          "type": "keyword"
        },
        "version": {
          "type": "keyword"
        }
      }
    }
}
```

#### Reindex codesystem-version to codesystem-version-tmp
```
POST _reindex
{
  "source": {
    "index": "codesystem-version"
  },
  "dest": {
    "index": "codesystem-version-tmp"
  },
  "script": {
    "source": """
    if (ctx._source.containsKey('importDate')) {
      def value = ctx._source['importDate'];
      // Try parsing the value as a date and convert to millis
      try {
        if (value instanceof String) {
          // Convert date to milliseconds
          ZonedDateTime zdt = ZonedDateTime.parse(value);
          long milliSinceEpoch = zdt.toInstant().toEpochMilli();
          ctx._source.importDate = milliSinceEpoch;
        }
      } catch (Exception e) {
        // If parsing fails, handle the failure (e.g., log an error, set a default value)
        ctx._source.importDate = 1000; // Set a default value for debug, adjust as needed
      }
    }
    """,
    "lang": "painless"
  }
}
```

#### Delete codesystem-version
```
DELETE codesystem-version
```

#### Reindex codesystem-version-tmp back to the original index name.
``` 
POST _reindex
{
  "source": {
    "index": "codesystem-version-tmp"
  },
  "dest": {
    "index": "codesystem-version"
  }
}
```

#### Delete codesystem-version-tmp
```
DELETE codesystem-version-tmp
```

#### Create a new index for branch-review-tmp with updated mapping
```
PUT branch-review-tmp
{
  "mappings":{
    "properties":{
      "_class":{
        "type":"text",
        "fields":{
          "keyword":{
            "type":"keyword",
            "ignore_above":256
          }
        }
      },
      "changedConcepts":{
        "type":"long"
      },
      "id":{
        "type":"keyword"
      },
      "lastUpdated":{
        "type":"long"
      },
      "source":{
        "type":"nested",
        "properties":{
          "baseTimestamp":{
            "type":"long"
          },
          "headTimestamp":{
            "type":"long"
          },
          "path":{
            "type":"text",
            "fields":{
              "keyword":{
                "type":"keyword",
                "ignore_above":256
              }
            }
          }
        }
      },
      "sourceIsParent":{
        "type":"boolean"
      },
      "status":{
        "type":"keyword"
      },
      "target":{
        "type":"nested",
        "properties":{
          "baseTimestamp":{
            "type":"long"
          },
          "headTimestamp":{
            "type":"long"
          },
          "path":{
            "type":"text",
            "fields":{
              "keyword":{
                "type":"keyword",
                "ignore_above":256
              }
            }
          }
        }
      }
    }
  }
}
```

#### Reindex branch-review to branch-review-tmp
```
POST _reindex
{
  "source": {
    "index": "branch-review"
  },
  "dest": {
    "index": "branch-review-tmp"
  },
  "script": {
    "source": """
    if (ctx._source.containsKey('lastUpdated')) {
      def value = ctx._source['lastUpdated'];
      // Try parsing the value as a date and convert to millis
      try {
        if (value instanceof String) {
          // Convert date to milliseconds
          ZonedDateTime zdt = ZonedDateTime.parse(value);
          long milliSinceEpoch = zdt.toInstant().toEpochMilli();
          ctx._source.lastUpdated = milliSinceEpoch;
        }
      } catch (Exception e) {
        // If parsing fails, handle the failure (e.g., log an error, set a default value)
        ctx._source.lastUpdated = 1000; // Set a default value for debug, adjust as needed
      }
    }
    """,
    "lang": "painless"
  }
}
```

#### Delete branch-review
```
DELETE branch-review
```

#### Reindex branch-review-tmp back to the original index name.
``` 
POST _reindex
{
  "source": {
    "index": "branch-review-tmp"
  },
  "dest": {
    "index": "branch-review"
  }
}
```

#### Delete branch-review-tmp
```
DELETE branch-review-tmp
```
Note: You can use curl for above operations if you don't have Kibana installed. See more details on [Reindex API](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html)

## Step two - Upgrade cluster to Elasticsearch 8

See [Elastic upgrade guide](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/setup-upgrade.html) for more information.
