package org.snomed.snowstorm.core.data.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
// Separate class to allow PermissionService to use Cacheable methods.
public class PermissionServiceCache {

	@Autowired
	private PermissionRecordRepository repository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Cacheable("permission-records")
	public List<PermissionRecord> findAllUsingCache() {
		return repository.findAll(PermissionService.PAGE_REQUEST).getContent();
	}

	@CacheEvict(value = "permission-records", allEntries = true)
	public void clearCache() {
		logger.info("Cleared permissions cache.");
	}

}
