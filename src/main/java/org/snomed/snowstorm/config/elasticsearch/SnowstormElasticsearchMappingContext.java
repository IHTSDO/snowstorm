package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchPersistentEntity;
import org.springframework.data.util.TypeInformation;

public class SnowstormElasticsearchMappingContext extends SimpleElasticsearchMappingContext implements ApplicationContextAware {

	private final String indexNamePrefix;
	private ApplicationContext context;

	public SnowstormElasticsearchMappingContext(String indexNamePrefix) {
		this.indexNamePrefix = indexNamePrefix;
	}

	@Override
	protected <T> SimpleElasticsearchPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		SimpleElasticsearchPersistentEntity<T> persistentEntity = new SnowstormSimpleElasticsearchPersistentEntity<>(indexNamePrefix, typeInformation);
		if (this.context != null) {
			persistentEntity.setApplicationContext(this.context);
		}

		return persistentEntity;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}
}
