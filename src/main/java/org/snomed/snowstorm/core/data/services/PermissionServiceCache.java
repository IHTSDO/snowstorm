package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
// Separate class to allow PermissionService to use Cacheable methods.
public class PermissionServiceCache {

	@Autowired
	private PermissionRecordRepository repository;

	@Cacheable("permission-records")
	public List<PermissionRecord> findAllUsingCache() {
		return repository.findAll(PermissionService.PAGE_REQUEST).getContent();
	}

}
