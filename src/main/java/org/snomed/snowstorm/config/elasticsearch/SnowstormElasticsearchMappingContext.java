package org.snomed.snowstorm.config.elasticsearch;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchPersistentEntity;
import org.springframework.data.spel.ExtensionAwareEvaluationContextProvider;
import org.springframework.data.util.TypeInformation;

public class SnowstormElasticsearchMappingContext extends SimpleElasticsearchMappingContext implements ApplicationContextAware {

	private final IndexConfig indexConfig;
	private ApplicationContext context;

	public SnowstormElasticsearchMappingContext(IndexConfig indexConfig) {
		this.indexConfig = indexConfig;
	}

	@Override
	protected <T> SimpleElasticsearchPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		SimpleElasticsearchPersistentEntity<T> persistentEntity = new SnowstormSimpleElasticsearchPersistentEntity<>(indexConfig, typeInformation);
		if (this.context != null) {
			persistentEntity.setEvaluationContextProvider(new ExtensionAwareEvaluationContextProvider(this.context));
		}
		return persistentEntity;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		super.setApplicationContext(context);
		this.context = context;
	}
}
