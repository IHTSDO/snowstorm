# Elasticsearch 9 upgrade guide

Snowstorm 12.0.0 requires **Elasticsearch 9.x** and cannot run against an 8.x server. The Elasticsearch client it now uses negotiates `compatible-with=9` media types, which an 8.x server rejects outright, so every request fails. Upgrading the cluster is not optional when upgrading to Snowstorm 12.

Follow the steps below to migrate an existing Elasticsearch 8.x cluster to 9.x. When running Snowstorm in a production environment, **take a snapshot before starting**.

You can skip this guide if you are trying Snowstorm for the first time — start with a fresh installation of Elasticsearch 9.x (tested against 9.5.2).

If you have been running Snowstorm in Docker and do not need to keep existing data, you can also skip this guide. However, you MUST remove the volume specified in [docker-compose.yml](../docker-compose.yml) before starting, or Elasticsearch 9 will start against a data directory it cannot read.

## No Snowstorm data migration is required

Unlike the [Elasticsearch 8 upgrade](elasticsearch8-upgrade.md), which needed the `codesystem-version` `importDate` field converting, this upgrade changes no Snowstorm index mappings. No field types changed in Snowstorm 12.0.0, so no reindex is needed on Snowstorm's account.

Everything below is Elasticsearch's own major-version upgrade procedure. The one step that catches Snowstorm users out is Step two.

## Step one - Upgrade the cluster to 8.19 first

You cannot jump straight from an arbitrary 8.x to 9.x. Upgrade the cluster to **8.19.x** first — this is the release that carries the Upgrade Assistant used in the next step.

See [Prepare to upgrade](https://www.elastic.co/docs/deploy-manage/upgrade/prepare-to-upgrade).

## Step two - Reindex or archive any indices created before 8.0.0

Elasticsearch supports indices created in the *previous* major version only. An index created by Elasticsearch 7.x remains readable in 8.x but **will block the upgrade to 9.x**.

This affects more Snowstorm installations than it first appears. The [Elasticsearch 8 upgrade guide](elasticsearch8-upgrade.md) performed an in-place cluster upgrade, so a cluster that followed it still holds Snowstorm indices *created* by 7.x, even though it has been running 8.x ever since. The running server version tells you nothing here — check the version each index was created with:

```
GET _all/_settings?filter_path=**.index.version.created_string
```

Any index reporting a 7.x creation version must be reindexed, archived, or deleted before upgrading. The Upgrade Assistant in Kibana 8.19 will list them and can reindex them or mark them read-only for you.

For Snowstorm indices, the simplest route is to reindex each affected index into a new one and swap it back, as shown in [Step one of the Elasticsearch 8 guide](elasticsearch8-upgrade.md#step-one---existing-codesystem-version-data-migration) — the same reindex/delete/reindex pattern, without the `importDate` script. If Snowstorm's data can be rebuilt from a release import instead, deleting the indices and re-importing is often quicker than reindexing.

Alternatively, snapshots of 7.x indices can be read in 9.x through the archive functionality without reindexing. See [Reading indices from older Elasticsearch versions](https://www.elastic.co/docs/deploy-manage/upgrade/deployment-or-cluster/reading-indices-from-older-elasticsearch-versions).

Do not skip this step — it is the most common cause of a failed 8-to-9 upgrade.

## Step three - Upgrade the cluster to Elasticsearch 9

See [Upgrade Elasticsearch](https://www.elastic.co/docs/deploy-manage/upgrade/deployment-or-cluster/elasticsearch).

## Step four - Start Snowstorm 12

Point Snowstorm at the upgraded cluster as usual:

```
java -Xms2g -Xmx4g -jar snowstorm.jar --elasticsearch.urls=http://localhost:9200
```

On startup Snowstorm initialises any missing indices and mappings against the existing data. Check the log for the Elasticsearch host and authentication lines to confirm it connected to the cluster you expect.

If both `elasticsearch.api-key` and `elasticsearch.username`/`elasticsearch.password` are configured, the API key takes precedence and a warning is logged. Remove whichever credential is stale to silence it.
