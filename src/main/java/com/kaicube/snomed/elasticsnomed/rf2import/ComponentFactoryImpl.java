package com.kaicube.snomed.elasticsnomed.rf2import;

import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;

import java.util.ArrayList;
import java.util.List;

public class ComponentFactoryImpl implements ComponentFactory {

	private Long2ObjectMap<Concept> conceptMap = new Long2ObjectOpenHashMap<>();
	private List<Concept> concepts = new ArrayList<>();

	@Override
	public void createConcept(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		final Concept concept = new Concept(conceptId, effectiveTime, isActive(active), moduleId, definitionStatusId);
		concepts.add(concept);
		conceptMap.put(Long.parseLong(conceptId), concept);
	}

	@Override
	public void addRelationship(String id, String effectiveTime, String active, String moduleId, String sourceId,
			String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		conceptMap.get(Long.parseLong(sourceId)).addRelationship(new Relationship(id, effectiveTime, isActive(active), moduleId, sourceId,
				destinationId, relationshipGroup, typeId, characteristicTypeId, modifierId));
	}

	@Override
	public void addDescription(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		conceptMap.get(Long.parseLong(caseSignificanceId)).addDescription(new Description(id, effectiveTime, isActive(active), moduleId, conceptId, languageCode, typeId, term, caseSignificanceId));
	}

	@Override
	public void addConceptParent(String sourceId, String parentId) {
	}

	@Override
	public void addConceptAttribute(String sourceId, String typeId, String valueId) {

	}

	@Override
	public void addConceptReferencedInRefsetId(String refsetId, String conceptId) {}

	@Override
	public void addConceptFSN(String conceptId, String term) {}

	public List<Concept> getConcepts() {
		return concepts;
	}

	private boolean isActive(String active) {
		return "1".equals(active);
	}
}