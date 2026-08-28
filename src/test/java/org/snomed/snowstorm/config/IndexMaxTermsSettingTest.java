package org.snomed.snowstorm.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.config.ElasticsearchConfig.INDEX_MAX_TERMS_COUNT;

/**
 * Covers the index.max_terms_count setting applied during startup by SnowstormApplication.run. Large ECL
 * queries fail against the Elasticsearch default of 65536, and nothing else exercises this path, so a
 * silent failure here would only show up in production as an ECL query rejected by Elasticsearch.
 *
 * Each case first moves the setting away from the configured value, so it is asserting a write rather than
 * whatever an earlier case happened to leave behind - index settings outlive the per-test document cleanup.
 */
class IndexMaxTermsSettingTest extends AbstractTest {

	private static final int ELASTICSEARCH_DEFAULT_MAX_TERMS_COUNT = 65536;

	@Autowired
	private TestConfig config;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ElasticsearchClient elasticsearchClient;

	@Value("${elasticsearch.index.max.terms.count}")
	private int configuredMaxTermsCount;

	@Test
	void testSettingIsAppliedToIndex() throws IOException {
		setMaxTermsCount(QueryConcept.class, ELASTICSEARCH_DEFAULT_MAX_TERMS_COUNT);

		config.updateIndexMaxTermsSetting(QueryConcept.class);

		assertEquals(configuredMaxTermsCount, maxTermsCountOf(QueryConcept.class));
	}

	@Test
	void testSettingIsAppliedToAllSnomedComponentIndices() throws IOException {
		setMaxTermsCount(Concept.class, ELASTICSEARCH_DEFAULT_MAX_TERMS_COUNT);

		config.updateIndexMaxTermsSettingForAllSnomedComponents();

		assertEquals(configuredMaxTermsCount, maxTermsCountOf(Concept.class));
	}

	@Test
	void testSecondPassReadsBackTheExistingSetting() throws IOException {
		setMaxTermsCount(QueryConcept.class, ELASTICSEARCH_DEFAULT_MAX_TERMS_COUNT);

		// The second call takes the other branch, reading back the value the first call wrote. A restart does
		// the same, so a type mismatch on the way out would break every startup after the first.
		config.updateIndexMaxTermsSetting(QueryConcept.class);
		config.updateIndexMaxTermsSetting(QueryConcept.class);

		assertEquals(configuredMaxTermsCount, maxTermsCountOf(QueryConcept.class));
	}

	private void setMaxTermsCount(Class<?> domainEntityClass, int maxTermsCount) throws IOException {
		elasticsearchClient.indices().putSettings(request -> request
				.index(indexNameOf(domainEntityClass))
				.settings(settings -> settings.maxTermsCount(maxTermsCount)));
		assertEquals(maxTermsCount, maxTermsCountOf(domainEntityClass), "Precondition: setting moved off the configured value");
	}

	private Integer maxTermsCountOf(Class<?> domainEntityClass) {
		Object value = elasticsearchOperations.indexOps(elasticsearchOperations.getIndexCoordinatesFor(domainEntityClass))
				.getSettings().get(INDEX_MAX_TERMS_COUNT);
		return value == null ? null : Integer.valueOf(String.valueOf(value));
	}

	private String indexNameOf(Class<?> domainEntityClass) {
		return elasticsearchOperations.getIndexCoordinatesFor(domainEntityClass).getIndexName();
	}
}
