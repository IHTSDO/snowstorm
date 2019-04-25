package org.snomed.snowstorm.config.elasticsearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchPersistentEntity;
import org.springframework.data.util.TypeInformation;

public class SnowstormElasticsearchMappingContext extends SimpleElasticsearchMappingContext implements ApplicationContextAware {

	private final IndexConfig indexConfig;
	private ApplicationContext context;
	private static final Logger LOGGER = LoggerFactory.getLogger(SnowstormElasticsearchMappingContext.class);

	public SnowstormElasticsearchMappingContext(IndexConfig indexConfig) {
		this.indexConfig = indexConfig;
	}

	@Override
	protected <T> SimpleElasticsearchPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		SimpleElasticsearchPersistentEntity<T> persistentEntity = new SnowstormSimpleElasticsearchPersistentEntity<>(indexConfig, typeInformation);
		if (this.context != null) {
			persistentEntity.setApplicationContext(this.context);
		}
		LOGGER.info("Creating index {} with {} shards and {} replicas.", persistentEntity.getIndexName(), persistentEntity.getShards(), persistentEntity.getReplicas());
		return persistentEntity;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}
}
