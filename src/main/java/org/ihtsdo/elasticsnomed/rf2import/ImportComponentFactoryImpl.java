package org.ihtsdo.elasticsnomed.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Entity;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.domain.Description;
import org.ihtsdo.elasticsnomed.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ImportComponentFactoryImpl implements ComponentFactory {

	public static final int FLUSH_INTERVAL = 1000;
	private final ConceptService conceptService;
	private final BranchService branchService;
	private final String path;
	private Commit commit;

	private PersistBuffer<Concept> conceptPersistBuffer;
	private PersistBuffer<Description> descriptionPersistBuffer;
	private PersistBuffer<Relationship> relationshipPersistBuffer;
	private PersistBuffer<ReferenceSetMember> memberPersistBuffer;
	private List<PersistBuffer> persistBuffers;
	private List<PersistBuffer> coreComponentPersistBuffers;

	private boolean coreComponentsFlushed;

	private Logger logger = LoggerFactory.getLogger(ImportComponentFactoryImpl.class);

	public ImportComponentFactoryImpl(ConceptService conceptService, BranchService branchService, String path) {
		this.conceptService = conceptService;
		this.branchService = branchService;
		this.path = path;
		persistBuffers = new ArrayList<>();
		coreComponentPersistBuffers = new ArrayList<>();
		conceptPersistBuffer = new PersistBuffer<Concept>() {
			@Override
			public void persistCollection(Collection<Concept> entities) {
				entities.stream().forEach(component -> component.setChanged(true));
				conceptService.doSaveBatchConcepts(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(conceptPersistBuffer);

		descriptionPersistBuffer = new PersistBuffer<Description>() {
			@Override
			public void persistCollection(Collection<Description> entities) {
				entities.stream().forEach(component -> component.setChanged(true));
				conceptService.doSaveBatchDescriptions(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(descriptionPersistBuffer);

		relationshipPersistBuffer = new PersistBuffer<Relationship>() {
			@Override
			public void persistCollection(Collection<Relationship> entities) {
				entities.stream().forEach(component -> component.setChanged(true));
				conceptService.doSaveBatchRelationships(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(relationshipPersistBuffer);

		memberPersistBuffer = new PersistBuffer<ReferenceSetMember>() {
			@Override
			public void persistCollection(Collection<ReferenceSetMember> entities) {
				synchronized (this) {
					if (!coreComponentsFlushed) {
						coreComponentPersistBuffers.forEach(PersistBuffer::flush);
						coreComponentsFlushed = true;
					}
				}
				entities.stream().forEach(component -> component.setChanged(true));
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
		completeImportCommit();
	}

	protected void completeImportCommit() {
		persistBuffers.stream().forEach(PersistBuffer::flush);
		conceptService.postProcess(commit);
		branchService.completeCommit(commit);
	}

	@Override
	public void createConcept(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		final Concept concept = new Concept(conceptId, effectiveTime, isActive(active), moduleId, definitionStatusId);
		if (effectiveTime != null) {
			concept.release(effectiveTime);
		}
		conceptPersistBuffer.save(concept);
	}

	@Override
	public void addRelationship(String id, String effectiveTime, String active, String moduleId, String sourceId,
			String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		final Relationship relationship = new Relationship(id, effectiveTime, isActive(active), moduleId, sourceId,
				destinationId, Integer.parseInt(relationshipGroup), typeId, characteristicTypeId, modifierId);
		if (effectiveTime != null) {
			relationship.release(effectiveTime);
		}
		relationshipPersistBuffer.save(relationship);
	}

	@Override
	public void addDescription(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		final Description description = new Description(id, effectiveTime, isActive(active), moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
		if (effectiveTime != null) {
			description.release(effectiveTime);
		}
		descriptionPersistBuffer.save(description);
	}

	@Override
	public void addReferenceSetMember(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		ReferenceSetMember member = new ReferenceSetMember(id, effectiveTime, isActive(active), moduleId, refsetId, referencedComponentId);
		for (int i = 6; i < fieldNames.length; i++) {
			member.setAdditionalField(fieldNames[i], otherValues[i - 6]);
		}
		if (effectiveTime != null) {
			member.release(effectiveTime);
		}
		memberPersistBuffer.save(member);
	}

	@Override
	public void addInferredConceptParent(String sourceId, String parentId) {
	}

	@Override
	public void removeInferredConceptParent(String sourceId, String destinationId) {
	}

	@Override
	public void addConceptAttribute(String sourceId, String typeId, String valueId) {

	}

	@Override
	public void addConceptReferencedInRefsetId(String refsetId, String conceptId) {}

	@Override
	public void addConceptFSN(String conceptId, String term) {}

	protected void setCommit(Commit commit) {
		this.commit = commit;
	}

	protected BranchService getBranchService() {
		return branchService;
	}

	private boolean isActive(String active) {
		return "1".equals(active);
	}

	private abstract class PersistBuffer<E extends Entity> {

		private List<E> entities = new ArrayList<>();

		public PersistBuffer() {
			persistBuffers.add(this);
		}

		public synchronized void save(E entity) {
			entities.add(entity);
			if (entities.size() >= FLUSH_INTERVAL) {
				flush();
			}
		}

		public synchronized void flush() {
			persistCollection(entities);
			entities.clear();
		}

		abstract void persistCollection(Collection<E> entities);

	}

}
