package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class DomainEntityConfiguration {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private ReferenceSetTypeRepository referenceSetTypeRepository;

	private Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepositoryMap;
	private Map<Class<? extends DomainEntity>, ElasticsearchCrudRepository> allTypeRepositoryMap;

	private Set<Class<? extends DomainEntity>> allTypes;

	@PostConstruct
	public void init() {
		componentTypeRepositoryMap = new LinkedHashMap<>();
		componentTypeRepositoryMap.put(Concept.class, conceptRepository);
		componentTypeRepositoryMap.put(Description.class, descriptionRepository);
		componentTypeRepositoryMap.put(Relationship.class, relationshipRepository);
		componentTypeRepositoryMap.put(ReferenceSetMember.class, referenceSetMemberRepository);

		allTypeRepositoryMap = new LinkedHashMap<>(componentTypeRepositoryMap);
		allTypeRepositoryMap.put(QueryConcept.class, queryConceptRepository);
		allTypeRepositoryMap.put(ReferenceSetType.class, referenceSetTypeRepository);

		allTypes = new HashSet<>();
		allTypes.addAll(componentTypeRepositoryMap.keySet());
		allTypes.add(QueryConcept.class);
		allTypes.add(ReferenceSetType.class);
	}

	public Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> getComponentTypeRepositoryMap() {
		return componentTypeRepositoryMap;
	}

	public Map<Class<? extends DomainEntity>, ElasticsearchCrudRepository> getAllTypeRepositoryMap() {
		return allTypeRepositoryMap;
	}

	public Set<Class<? extends DomainEntity>> getAllDomainEntityTypes() {
		return allTypes;
	}
}
