# Elasticsearch 9 upgrade guide

Snowstorm 12.0.0 requires **Elasticsearch 9.5.5 or later** and cannot run against an 8.x server. The Elasticsearch client it now uses negotiates `compatible-with=9` media types, which an 8.x server rejects outright, so every request fails. Upgrading the cluster is not optional when upgrading to Snowstorm 12.

The cluster upgrade is one-way: a 9.x cluster cannot be downgraded to 8.x. **Take a snapshot before starting** — restoring it to an 8.x cluster is the only way back.

You can skip this guide if you are trying Snowstorm for the first time — start with a fresh installation of Elasticsearch 9.5.5 or later.

If you have been running Snowstorm in Docker and do not need to keep existing data, you can also skip this guide. However, you MUST remove the `elastic` volume declared in [docker-compose.yml](../docker-compose.yml) before starting, for example with `docker compose down -v`, or Elasticsearch 9 will start against a data directory it cannot read. You will then need to import your release files again.

## Snowstorm itself needs no data migration

Snowstorm 12.0.0 changes no index mappings, so unlike the [Elasticsearch 8 upgrade](elasticsearch8-upgrade.md) there is no Snowstorm field to convert.

Elasticsearch may still require some indices to be reindexed for its own reasons. That is Step two, and it is the step most likely to block the upgrade.

## Order of upgrade

Stop Snowstorm before Step two and keep it stopped until the cluster is on 9.5.5 or later:

1. Upgrade the cluster to 8.19 (Step one)
2. Stop Snowstorm, then reindex or remove indices created before 8.0.0 (Step two)
3. Upgrade the cluster to 9.5.5 or later (Step three)
4. Start Snowstorm 12 (Step four)

## Step one - Upgrade the cluster to 8.19 first

You cannot jump straight from an arbitrary 8.x to 9.x. Upgrade the cluster to **8.19.x** first — this is the release that can upgrade directly to 9.x and that carries the Upgrade Assistant.

See [Prepare to upgrade](https://www.elastic.co/docs/deploy-manage/upgrade/prepare-to-upgrade).

## Step two - Reindex or remove any indices created before 8.0.0

Elasticsearch supports indices created in the *previous* major version only. An index created by Elasticsearch 7.x remains readable in 8.x but **will block the upgrade to 9.x**.

This affects more Snowstorm installations than it first appears. The [Elasticsearch 8 upgrade guide](elasticsearch8-upgrade.md) performed an in-place cluster upgrade, so a cluster that followed it still holds Snowstorm indices *created* by 7.x, even though it has been running 8.x ever since. The running server version tells you nothing here — check the version each index was created with:

```
GET _all/_settings?human&filter_path=**.index.version.created_string
```

Any index reporting a 7.x creation version must be dealt with before upgrading. Without Kibana, the deprecation API lists the indices blocking the upgrade:

```
GET _migration/deprecations
```

Snowstorm writes to all of its indices, so marking them read-only, which the Upgrade Assistant offers, is **not** an option for Snowstorm indices. Use one of the following instead.

### Option 1 - Delete and re-import

If all Snowstorm content comes from release imports, with no authoring in this instance, delete the Snowstorm indices and import the release files again once on Snowstorm 12. For large indices such as `description` and `member` this is usually quicker than reindexing.

Do not use this option if the instance holds authored content that is not in a release package; it would be lost.

### Option 2 - Reindex with the Upgrade Assistant

The Upgrade Assistant in Kibana 8.19 lists the affected indices and can reindex them for you, keeping their mappings and settings.

### Option 3 - Reindex manually

For each affected index (shown here as `<index>`):

1. Save the mapping: `GET <index>/_mapping`
2. Create `<index>-tmp` with that mapping: `PUT <index>-tmp` with `{"mappings": <mappings from step 1>}`
3. Copy the data: `POST _reindex` with `{"source": {"index": "<index>"}, "dest": {"index": "<index>-tmp"}}`
4. Check the document counts match: `GET _cat/count/<index>` and `GET _cat/count/<index>-tmp`
5. `DELETE <index>`
6. Recreate `<index>` with the same mapping, as in step 2
7. Copy the data back: `POST _reindex` from `<index>-tmp` to `<index>`
8. Check the counts again, then `DELETE <index>-tmp`

Always create the target index with the saved mapping first. Reindexing into an index that does not exist lets Elasticsearch guess the field types, which will not match Snowstorm's mappings.

### Alternatively - Read 7.x indices from a snapshot

Snapshots of 7.x indices can be read in 9.x through the archive functionality without reindexing. See [Reading indices from older Elasticsearch versions](https://www.elastic.co/docs/deploy-manage/upgrade/deployment-or-cluster/reading-indices-from-older-elasticsearch-versions).

## Step three - Upgrade the cluster to Elasticsearch 9.5.5 or later

If you use Kibana, upgrade it to the same version.

See [Upgrade Elasticsearch](https://www.elastic.co/docs/deploy-manage/upgrade/deployment-or-cluster/elasticsearch).

## Step four - Start Snowstorm 12

Start Snowstorm 12 against the upgraded cluster as usual:

```
java -Xms2g -Xmx4g -jar snowstorm.jar --elasticsearch.urls=http://localhost:9200
```

On startup Snowstorm initialises any missing indices and mappings against the existing data. Check the log for the Elasticsearch host and authentication lines to confirm it connected to the cluster you expect.
