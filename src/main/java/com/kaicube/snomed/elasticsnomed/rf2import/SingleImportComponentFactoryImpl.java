package com.kaicube.snomed.elasticsnomed.rf2import;

import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.services.BranchService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;

import java.util.*;

public class SingleImportComponentFactoryImpl implements ComponentFactory {

	public static final int FLUSH_INTERVAL = 10000;
	private final BranchService branchService;
	private final String path;
	private Commit commit;

	private PersistBuffer<Concept> conceptPersistBuffer;
	private PersistBuffer<Description> descriptionPersistBuffer;
	private PersistBuffer<Relationship> relationshipPersistBuffer;
	private PersistBuffer<ReferenceSetMember> memberPersistBuffer;
	private Set<PersistBuffer> persistBuffers;

	public SingleImportComponentFactoryImpl(ConceptService conceptService, BranchService branchService, String path) {
		this.branchService = branchService;
		this.path = path;
		persistBuffers = new HashSet<>();
		conceptPersistBuffer = new PersistBuffer<Concept>() {
			@Override
			public void persistCollection(Collection<Concept> entities) {
				conceptService.doSaveBatchConcepts(entities, commit);
			}
		};
		descriptionPersistBuffer = new PersistBuffer<Description>() {
			@Override
			public void persistCollection(Collection<Description> entities) {
				conceptService.doSaveBatchDescriptions(entities, commit);
			}
		};
		relationshipPersistBuffer = new PersistBuffer<Relationship>() {
			@Override
			public void persistCollection(Collection<Relationship> entities) {
				conceptService.doSaveBatchRelationships(entities, commit);
			}
		};
		memberPersistBuffer = new PersistBuffer<ReferenceSetMember>() {
			@Override
			public void persistCollection(Collection<ReferenceSetMember> entities) {
				conceptService.doSaveBatchMembers(entities, commit);
			}
		};
	}

	@Override
	public void loadingComponentsStarting() {
		commit = branchService.openCommit(path);
	}

	@Override
	public void loadingComponentsCompleted() {
		persistBuffers.stream().forEach(PersistBuffer::flush);
		branchService.completeCommit(commit);
	}

	@Override
	public void createConcept(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		final Concept concept = new Concept(conceptId, effectiveTime, isActive(active), moduleId, definitionStatusId);
		conceptPersistBuffer.save(concept);
	}

	@Override
	public void addRelationship(String id, String effectiveTime, String active, String moduleId, String sourceId,
			String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		relationshipPersistBuffer.save(new Relationship(id, effectiveTime, isActive(active), moduleId, sourceId,
				destinationId, Integer.parseInt(relationshipGroup), typeId, characteristicTypeId, modifierId));
	}

	@Override
	public void addDescription(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		descriptionPersistBuffer.save(new Description(id, effectiveTime, isActive(active), moduleId, conceptId, languageCode, typeId, term, caseSignificanceId));
	}

	@Override
	public void addReferenceSetMember(String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		memberPersistBuffer.save(new LanguageReferenceSetMember(id, effectiveTime, isActive(active), moduleId, refsetId, referencedComponentId, otherValues[0]));
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

	private boolean isActive(String active) {
		return "1".equals(active);
	}

	private abstract class PersistBuffer<E extends Entity> {

		private List<E> entities = new ArrayList<>();

		public PersistBuffer() {
			persistBuffers.add(this);
		}

		public void save(E entity) {
			entities.add(entity);
			if (entities.size() >= FLUSH_INTERVAL) {
				flush();
			}
		}

		public void flush() {
			persistCollection(entities);
			entities.clear();
		}

		public abstract void persistCollection(Collection<E> entities);

	}

}
