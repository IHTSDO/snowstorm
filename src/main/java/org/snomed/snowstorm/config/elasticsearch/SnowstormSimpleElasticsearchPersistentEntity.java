package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.util.TypeInformation;

public class SnowstormSimpleElasticsearchPersistentEntity<T> {

	private final IndexConfig indexConfig;
	private final String indexNamePrefix;


	SnowstormSimpleElasticsearchPersistentEntity(IndexConfig indexConfig, TypeInformation<T> typeInformation) {
		this.indexConfig = indexConfig;
		this.indexNamePrefix = indexConfig.getIndexNamePrefix() != null ? indexConfig.getIndexNamePrefix() : "";
	}

	public IndexCoordinates getIndexCoordinates() {
		return IndexCoordinates.of(indexNamePrefix);
	}

	public short getShards() {
		return indexConfig.getIndexShards();
	}

	public short getReplicas() {
		return indexConfig.getIndexReplicas();
	}
}
