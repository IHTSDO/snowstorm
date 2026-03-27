package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodeSystemQueryService {

	private final CodeSystemRepository repository;

	public CodeSystemQueryService(CodeSystemRepository repository) {
		this.repository = repository;
	}

	@Cacheable("code-systems")
	public List<CodeSystem> findAllStored() {
		return repository.findAll(PageRequest.of(0, 10_000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent();
	}
}
