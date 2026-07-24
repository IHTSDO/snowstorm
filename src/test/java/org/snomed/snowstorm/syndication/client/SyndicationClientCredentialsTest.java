package org.snomed.snowstorm.syndication.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.InstallationTask;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class SyndicationClientCredentialsTest {

	@Test
	void isConfigCredentialsConfiguredWhenBothPropertiesSet() throws JAXBException {
		SyndicationClient client = new SyndicationClient("http://localhost", "user", "pass");
		assertTrue(client.isConfigCredentialsConfigured());
	}

	@Test
	void isConfigCredentialsConfiguredWhenPropertiesEmpty() throws JAXBException {
		SyndicationClient client = new SyndicationClient("http://localhost", "", "");
		assertFalse(client.isConfigCredentialsConfigured());
	}

	@Test
	void resolveCredentialsPrefersConfigOverRequest() throws JAXBException, ServiceException {
		SyndicationClient client = new SyndicationClient("http://localhost", "configUser", "configPass");
		Pair<String, String> resolved = client.resolveCredentials(Pair.of("requestUser", "requestPass"));
		assertEquals("configUser", resolved.getFirst());
		assertEquals("configPass", resolved.getSecond());
	}

	@Test
	void resolveCredentialsUsesRequestWhenConfigEmpty() throws JAXBException, ServiceException {
		SyndicationClient client = new SyndicationClient("http://localhost", "", "");
		Pair<String, String> resolved = client.resolveCredentials(Pair.of("requestUser", "requestPass"));
		assertEquals("requestUser", resolved.getFirst());
		assertEquals("requestPass", resolved.getSecond());
	}

	@Test
	void resolveCredentialsThrowsWhenNeitherConfigured() throws JAXBException {
		SyndicationClient client = new SyndicationClient("http://localhost", "", "");
		ServiceException exception = assertThrows(ServiceException.class,
				() -> client.resolveCredentials(Pair.of("", "")));
		assertTrue(exception.getMessage().contains("MLDS credentials are not configured"));
	}

	@Test
	void installationTaskDoesNotSerializeCredentials() throws Exception {
		InstallationTask task = new InstallationTask(
				"http://snomed.info/sct/900000000000207008", "20250301", null, "secretUser", "secretPass",
				SecurityContextHolder.getContext());
		String json = new ObjectMapper().writeValueAsString(task);
		assertFalse(json.contains("secretUser"));
		assertFalse(json.contains("secretPass"));
		assertFalse(json.contains("username"));
		assertFalse(json.contains("password"));
	}
}
