package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ConceptRepository;
import org.snomed.snowstorm.core.data.repositories.DescriptionRepository;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.repositories.RelationshipRepository;
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

	private Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepositoryMap;

	private Set<Class<? extends DomainEntity>> allTypes;

	@PostConstruct
	public void init() {
		componentTypeRepositoryMap = new LinkedHashMap<>();
		componentTypeRepositoryMap.put(Concept.class, conceptRepository);
		componentTypeRepositoryMap.put(Description.class, descriptionRepository);
		componentTypeRepositoryMap.put(Relationship.class, relationshipRepository);
		componentTypeRepositoryMap.put(ReferenceSetMember.class, referenceSetMemberRepository);

		allTypes = new HashSet<>();
		allTypes.addAll(componentTypeRepositoryMap.keySet());
		allTypes.add(QueryConcept.class);
		allTypes.add(ReferenceSetType.class);
	}

	public Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> getComponentTypeRepositoryMap() {
		return componentTypeRepositoryMap;
	}

	public Set<Class<? extends DomainEntity>> getAllDomainEntityTypes() {
		return allTypes;
	}
}
