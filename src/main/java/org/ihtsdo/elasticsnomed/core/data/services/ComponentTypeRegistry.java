package org.ihtsdo.elasticsnomed.core.data.services;

import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.repositories.ConceptRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.DescriptionRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.ReferenceSetMemberRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.RelationshipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ComponentTypeRegistry {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	private Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepositoryMap;

	@PostConstruct
	public void init() {
		componentTypeRepositoryMap = new LinkedHashMap<>();
		componentTypeRepositoryMap.put(Concept.class, conceptRepository);
		componentTypeRepositoryMap.put(Description.class, descriptionRepository);
		componentTypeRepositoryMap.put(Relationship.class, relationshipRepository);
		componentTypeRepositoryMap.put(ReferenceSetMember.class, referenceSetMemberRepository);
	}

	public Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> getComponentTypeRepositoryMap() {
		return componentTypeRepositoryMap;
	}

}
