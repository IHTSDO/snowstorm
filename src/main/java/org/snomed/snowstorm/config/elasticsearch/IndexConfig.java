package org.snomed.snowstorm.config.elasticsearch;

public class IndexConfig {

	private final String indexNamePrefix;
	private final short indexShards;
	private final short indexReplicas;

	public IndexConfig(String indexNamePrefix, short indexShards, short indexReplicas) {
		if (indexShards < 1) {
			throw new IllegalArgumentException("Number of index shards must be 1 or more.");
		}
		this.indexNamePrefix = indexNamePrefix;
		this.indexShards = indexShards;
		this.indexReplicas = indexReplicas;
	}

	public String getIndexNamePrefix() {
		return indexNamePrefix;
	}

	public short getIndexShards() {
		return indexShards;
	}

	public short getIndexReplicas() {
		return indexReplicas;
	}
}
