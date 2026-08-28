package org.snomed.snowstorm.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.rest5_client.Rest5Clients;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * spring-data-elasticsearch 6 builds a Rest5Client, and Rest5Clients picks the client configurer callbacks out
 * of the configuration by instanceof against its own five callback types. A callback of any other type - the
 * deprecated rest_client.RestClients equivalents, for instance - is dropped silently, taking the timeouts,
 * connection pool sizing, credentials and AWS request signing with it: no exception, no log line.
 *
 * These are plain unit tests because TestConfig overrides clientConfiguration() to point at the test container,
 * so no Spring test exercises the configuration this class builds.
 */
class ElasticsearchClientConfigurationTest {

	private static final String API_KEY_HEADER_VALUE = "ApiKey test-api-key";

	@Test
	void testEveryClientConfigurerIsATypeRest5ClientsApplies() {
		List<ClientConfiguration.ClientConfigurationCallback<?>> configurers =
				clientConfiguration("", "", "", true).getClientConfigurers();

		assertFalse(configurers.isEmpty());
		assertTrue(configurers.stream().allMatch(configurer ->
						configurer instanceof Rest5Clients.ElasticsearchRequestConfigCallback
						|| configurer instanceof Rest5Clients.ElasticsearchRest5ClientConfigurationCallback
						|| configurer instanceof Rest5Clients.ElasticsearchHttpClientConfigurationCallback
						|| configurer instanceof Rest5Clients.ElasticsearchConnectionConfigurationCallback
						|| configurer instanceof Rest5Clients.ElasticsearchConnectionManagerCallback),
				() -> "Configurers Rest5Clients would ignore: " + configurers.stream()
						.map(configurer -> configurer.getClass().getName()).toList());
	}

	@Test
	void testTimeoutsAndConnectionPoolAreConfigured() {
		ClientConfiguration clientConfiguration = clientConfiguration("", "", "", false);

		// Long running ECL and semantic index queries rely on the socket timeout being well above the default
		assertEquals(Duration.ofSeconds(10), clientConfiguration.getConnectTimeout());
		assertEquals(Duration.ofSeconds(360), clientConfiguration.getSocketTimeout());
		assertTrue(hasCallback(clientConfiguration, Rest5Clients.ElasticsearchRequestConfigCallback.class),
				"Connection request timeout is applied through a request config callback");
		assertTrue(hasCallback(clientConfiguration, Rest5Clients.ElasticsearchConnectionManagerCallback.class),
				"Connection pool size is applied through a connection manager callback");
	}

	@Test
	void testBasicAuthenticationIsSent() {
		ClientConfiguration clientConfiguration = clientConfiguration("elastic-user", "elastic-password", "", false);

		assertEquals(List.of("Basic " + base64("elastic-user:elastic-password")),
				clientConfiguration.getDefaultHeaders().get("Authorization"));
	}

	@Test
	void testApiKeyIsSentWhenNoUsernameIsConfigured() {
		ClientConfiguration clientConfiguration = clientConfiguration("", "", "test-api-key", false);

		assertEquals(List.of(API_KEY_HEADER_VALUE), clientConfiguration.getDefaultHeaders().get("Authorization"));
	}

	@Test
	void testApiKeyTakesPrecedenceOverBasicAuthentication() {
		// Both end up on the same header, so one has to win. The API key wins, as it did before the move to
		// spring-data-elasticsearch 6, so a deployment carrying stale username/password alongside a working
		// API key still authenticates. A warning is logged when both are configured.
		ClientConfiguration clientConfiguration = clientConfiguration("elastic-user", "elastic-password", "test-api-key", false);

		assertEquals(List.of(API_KEY_HEADER_VALUE), clientConfiguration.getDefaultHeaders().get("Authorization"));
	}

	@Test
	void testNoAuthorizationHeaderWhenNothingIsConfigured() {
		ClientConfiguration clientConfiguration = clientConfiguration("", "", "", false);

		assertFalse(clientConfiguration.getDefaultHeaders().containsKey("Authorization"));
	}

	@Test
	void testAwsRequestSigningIsAppliedOnlyWhenEnabled() {
		assertTrue(hasCallback(clientConfiguration("", "", "", true), Rest5Clients.ElasticsearchHttpClientConfigurationCallback.class),
				"AWS request signing interceptor is registered when signing is enabled");
		assertFalse(hasCallback(clientConfiguration("", "", "", false), Rest5Clients.ElasticsearchHttpClientConfigurationCallback.class),
				"No http client callback when signing is disabled");
	}

	@Test
	void testSslIsUsedForHttpsHosts() {
		assertFalse(clientConfiguration("", "", "", false).useSsl());
		assertTrue(clientConfiguration("https://elasticsearch.example.com:9200", "", "", "", false).useSsl());
	}

	@Test
	void testConfigurationBuildsAClient() {
		// Building the client is what runs the callback bodies - the timeouts and pool sizes above are only
		// assertions about registration. No connection is opened here. AWS signing stays off: building that
		// interceptor resolves a region from the environment, which is not something to depend on in a test.
		try (Rest5Client client = Rest5Clients.getRest5Client(clientConfiguration("elastic-user", "elastic-password", "", false))) {
			assertNotNull(client);
		} catch (IOException e) {
			throw new AssertionError("Failed to close the Elasticsearch client", e);
		}
	}

	private ClientConfiguration clientConfiguration(String username, String password, String apiKey, boolean awsRequestSigning) {
		return clientConfiguration("http://localhost:9200", username, password, apiKey, awsRequestSigning);
	}

	private ClientConfiguration clientConfiguration(String url, String username, String password, String apiKey, boolean awsRequestSigning) {
		ElasticsearchProperties properties = new ElasticsearchProperties();
		properties.setUrls(new String[] { url });

		ElasticsearchConfig config = new ElasticsearchConfig() {
			@Override
			public ElasticsearchProperties elasticsearchProperties() {
				return properties;
			}
		};
		ReflectionTestUtils.setField(config, ElasticsearchConfig.class, "elasticsearchUsername", username, String.class);
		ReflectionTestUtils.setField(config, ElasticsearchConfig.class, "elasticsearchPassword", password, String.class);
		ReflectionTestUtils.setField(config, ElasticsearchConfig.class, "apiKey", apiKey, String.class);
		ReflectionTestUtils.setField(config, ElasticsearchConfig.class, "awsRequestSigning", awsRequestSigning, Boolean.class);

		return config.clientConfiguration();
	}

	private boolean hasCallback(ClientConfiguration clientConfiguration, Class<?> callbackType) {
		return clientConfiguration.getClientConfigurers().stream().anyMatch(callbackType::isInstance);
	}

	private String base64(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes());
	}
}
