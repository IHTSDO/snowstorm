package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

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

	private Map<Class<? extends SnomedComponent<?>>, ElasticsearchRepository> componentTypeRepositoryMap;
	private Map<Class<? extends DomainEntity<?>>, ElasticsearchRepository> allTypeRepositoryMap;

	private Set<Class<? extends DomainEntity<?>>> allTypes;
	private Map<Class<? extends DomainEntity<?>>, String> allIdFields;

	@PostConstruct
	public void init() {
		componentTypeRepositoryMap = new LinkedHashMap<>();
		componentTypeRepositoryMap.put(Concept.class, conceptRepository);
		componentTypeRepositoryMap.put(Description.class, descriptionRepository);
		componentTypeRepositoryMap.put(Relationship.class, relationshipRepository);
		componentTypeRepositoryMap.put(ReferenceSetMember.class, referenceSetMemberRepository);
		componentTypeRepositoryMap = Collections.unmodifiableMap(componentTypeRepositoryMap);

		allTypeRepositoryMap = new LinkedHashMap<>(componentTypeRepositoryMap);
		allTypeRepositoryMap.put(QueryConcept.class, queryConceptRepository);
		allTypeRepositoryMap.put(ReferenceSetType.class, referenceSetTypeRepository);
		allTypeRepositoryMap = Collections.unmodifiableMap(allTypeRepositoryMap);

		allTypes = new HashSet<>();
		allTypes.addAll(componentTypeRepositoryMap.keySet());
		allTypes.add(QueryConcept.class);
		allTypes.add(ReferenceSetType.class);
		allTypes = Collections.unmodifiableSet(allTypes);

		allIdFields = new HashMap<>();
		allIdFields.put(Concept.class, Concept.Fields.CONCEPT_ID);
		allIdFields.put(Description.class, Description.Fields.DESCRIPTION_ID);
		allIdFields.put(Relationship.class, Relationship.Fields.RELATIONSHIP_ID);
		allIdFields.put(ReferenceSetMember.class, ReferenceSetMember.Fields.MEMBER_ID);
		allIdFields.put(QueryConcept.class, QueryConcept.Fields.CONCEPT_ID_FORM);
		allIdFields.put(ReferenceSetType.class, ReferenceSetType.Fields.CONCEPT_ID);
		allIdFields = Collections.unmodifiableMap(allIdFields);
	}

	public Map<Class<? extends SnomedComponent<?>>, ElasticsearchRepository> getComponentTypeRepositoryMap() {
		return componentTypeRepositoryMap;
	}

	public Map<Class<? extends DomainEntity<?>>, ElasticsearchRepository> getAllTypeRepositoryMap() {
		return allTypeRepositoryMap;
	}

	public Set<Class<? extends DomainEntity<?>>> getAllDomainEntityTypes() {
		return allTypes;
	}

	public Map<Class<? extends DomainEntity<?>>, String> getAllIdFields() {
		return allIdFields;
	}
}
