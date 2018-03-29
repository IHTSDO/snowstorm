package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchPersistentEntity;
import org.springframework.data.util.TypeInformation;

public class SnowstormSimpleElasticsearchPersistentEntity<T> extends SimpleElasticsearchPersistentEntity<T> {

	private final String indexNamePrefix;

	public SnowstormSimpleElasticsearchPersistentEntity(String indexNamePrefix, TypeInformation<T> typeInformation) {
		super(typeInformation);
		this.indexNamePrefix = indexNamePrefix != null ? indexNamePrefix : "";
	}

	@Override
	public String getIndexName() {
		return indexNamePrefix + super.getIndexName();
	}
}
