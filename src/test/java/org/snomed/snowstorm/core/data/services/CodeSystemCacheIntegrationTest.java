package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@Import(CodeSystemCacheIntegrationTest.CacheTestConfiguration.class)
class CodeSystemCacheIntegrationTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemQueryService codeSystemQueryService;

	@Autowired
	private CacheManager cacheManager;

	@Test
	void createCodeSystemShouldEvictCodeSystemAndBranchCaches() {
		// Prime both caches
		createAndPrimeCaches();

		// Creating another code system should evict both caches
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST"));
		assertCodeSystemCachesEmpty();
	}

	@Test
	void failedCreateCodeSystemShouldNotEvictCaches() {
		// Prime both caches
		createAndPrimeCaches();

		// Method failure should not evict because @CacheEvict runs after successful invocation by default
		CodeSystem duplicateCodeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		assertThrows(IllegalArgumentException.class, () -> codeSystemService.createCodeSystem(duplicateCodeSystem));
		assertCodeSystemCachesPresent();
	}

	@Test
	void updateCodeSystemShouldEvictCodeSystemAndBranchCaches() {
		createAndPrimeCaches();

		CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.update(codeSystem, new CodeSystemUpdateRequest("Updated name", "Updated owner"));

		assertCodeSystemCachesEmpty();
	}

	@Test
	void deleteCodeSystemShouldEvictCodeSystemAndBranchCaches() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST"));
		codeSystemQueryService.findAllStored();
		codeSystemService.findAllCodeSystemBranchesUsingCache();
		assertCodeSystemCachesPresent();

		codeSystemService.deleteCodeSystemAndVersions(extension, true);
		assertCodeSystemCachesEmpty();
	}

	private void createAndPrimeCaches() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemQueryService.findAllStored();
		codeSystemService.findAllCodeSystemBranchesUsingCache();
		assertCodeSystemCachesPresent();
	}

	private void assertCodeSystemCachesPresent() {
		assertNotNull(getCache("code-systems").get(SimpleKey.EMPTY), "Expected code-systems cache entry.");
		assertNotNull(getCache("code-system-branches").get(SimpleKey.EMPTY), "Expected code-system-branches cache entry.");
	}

	private void assertCodeSystemCachesEmpty() {
		assertNull(getCache("code-systems").get(SimpleKey.EMPTY), "Expected code-systems cache eviction.");
		assertNull(getCache("code-system-branches").get(SimpleKey.EMPTY), "Expected code-system-branches cache eviction.");
	}

	private Cache getCache(String cacheName) {
		Cache cache = cacheManager.getCache(cacheName);
		assertNotNull(cache, "Cache '" + cacheName + "' should be configured.");
		return cache;
	}

	@TestConfiguration
	@EnableCaching
	static class CacheTestConfiguration {
		@Bean
		CacheManager cacheManager() {
			return new ConcurrentMapCacheManager("code-systems", "code-system-branches", "permission-records");
		}
	}
}
