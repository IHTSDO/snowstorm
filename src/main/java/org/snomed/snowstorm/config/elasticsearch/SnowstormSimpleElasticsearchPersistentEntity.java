package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchPersistentEntity;
import org.springframework.data.util.TypeInformation;

public class SnowstormSimpleElasticsearchPersistentEntity<T> extends SimpleElasticsearchPersistentEntity<T> {

	private final IndexConfig indexConfig;
	private final String indexNamePrefix;

	SnowstormSimpleElasticsearchPersistentEntity(IndexConfig indexConfig, TypeInformation<T> typeInformation) {
		super(typeInformation);
		this.indexConfig = indexConfig;
		this.indexNamePrefix = indexConfig.getIndexNamePrefix() != null ? indexConfig.getIndexNamePrefix() : "";
	}

	@Override
	public String getIndexName() {
		return indexNamePrefix + super.getIndexName();
	}

	@Override
	public short getShards() {
		return indexConfig.getIndexShards();
	}

	@Override
	public short getReplicas() {
		return indexConfig.getIndexReplicas();
	}
}
