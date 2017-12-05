package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Entity;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ImportComponentFactoryImpl extends ImpotentComponentFactory {

	private static final int FLUSH_INTERVAL = 5000;
	private static final int MEMBER_ADDITIONAL_FIELD_OFFSET = 6;
	private final BranchService branchService;
	private final String path;
	private Commit commit;

	private PersistBuffer<Concept> conceptPersistBuffer;
	private PersistBuffer<Description> descriptionPersistBuffer;
	private PersistBuffer<Relationship> relationshipPersistBuffer;
	private PersistBuffer<ReferenceSetMember> memberPersistBuffer;
	private List<PersistBuffer> persistBuffers;
	private List<PersistBuffer> coreComponentPersistBuffers;
	private MaxEffectiveTimeCollector maxEffectiveTimeCollector;

	protected boolean coreComponentsFlushed;

	ImportComponentFactoryImpl(ConceptService conceptService, BranchService branchService, String path) {
		this.branchService = branchService;
		this.path = path;
		persistBuffers = new ArrayList<>();
		maxEffectiveTimeCollector = new MaxEffectiveTimeCollector();
		coreComponentPersistBuffers = new ArrayList<>();
		conceptPersistBuffer = new PersistBuffer<Concept>() {
			@Override
			public void persistCollection(Collection<Concept> entities) {
				entities.forEach(component -> {
					component.setChanged(true);
					maxEffectiveTimeCollector.add(component.getEffectiveTime());
				});
				conceptService.doSaveBatchConcepts(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(conceptPersistBuffer);

		descriptionPersistBuffer = new PersistBuffer<Description>() {
			@Override
			public void persistCollection(Collection<Description> entities) {
				entities.forEach(component -> {
					component.setChanged(true);
					maxEffectiveTimeCollector.add(component.getEffectiveTime());
				});
				conceptService.doSaveBatchDescriptions(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(descriptionPersistBuffer);

		relationshipPersistBuffer = new PersistBuffer<Relationship>() {
			@Override
			public void persistCollection(Collection<Relationship> entities) {
				entities.forEach(component -> {
					component.setChanged(true);
					maxEffectiveTimeCollector.add(component.getEffectiveTime());
				});
				conceptService.doSaveBatchRelationships(entities, commit);
			}
		};
		coreComponentPersistBuffers.add(relationshipPersistBuffer);

		memberPersistBuffer = new PersistBuffer<ReferenceSetMember>() {
			@Override
			public void persistCollection(Collection<ReferenceSetMember> entities) {
				if (!coreComponentsFlushed) { // Avoid having to sync to check this
					synchronized (this) {
						if (!coreComponentsFlushed) {
							coreComponentPersistBuffers.forEach(PersistBuffer::flush);
							coreComponentsFlushed = true;
						}
					}
				}
				entities.forEach(component -> {
					component.setChanged(true);
					maxEffectiveTimeCollector.add(component.getEffectiveTime());
				});
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

	void completeImportCommit() {
		persistBuffers.forEach(PersistBuffer::flush);
		commit.markSuccessful();
		commit.close();
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		final Concept concept = new Concept(conceptId, effectiveTime, isActive(active), moduleId, definitionStatusId);
		if (effectiveTime != null) {
			concept.release(effectiveTime);
		}
		conceptPersistBuffer.save(concept);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId,
									 String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		final Relationship relationship = new Relationship(id, effectiveTime, isActive(active), moduleId, sourceId,
				destinationId, Integer.parseInt(relationshipGroup), typeId, characteristicTypeId, modifierId);
		if (effectiveTime != null) {
			relationship.release(effectiveTime);
		}
		relationshipPersistBuffer.save(relationship);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode,
									String typeId, String term, String caseSignificanceId) {
		final Description description = new Description(id, effectiveTime, isActive(active), moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
		if (effectiveTime != null) {
			description.release(effectiveTime);
		}
		descriptionPersistBuffer.save(description);
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId,
										   String referencedComponentId, String... otherValues) {
		ReferenceSetMember member = new ReferenceSetMember(id, effectiveTime, isActive(active), moduleId, refsetId, referencedComponentId);
		for (int i = MEMBER_ADDITIONAL_FIELD_OFFSET; i < fieldNames.length; i++) {
			member.setAdditionalField(fieldNames[i], otherValues[i - MEMBER_ADDITIONAL_FIELD_OFFSET]);
		}
		if (effectiveTime != null) {
			member.release(effectiveTime);
		}
		memberPersistBuffer.save(member);
	}

	public String getMaxEffectiveTime() {
		return maxEffectiveTimeCollector.getMaxEffectiveTime();
	}

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

		PersistBuffer() {
			persistBuffers.add(this);
		}

		synchronized void save(E entity) {
			entities.add(entity);
			if (entities.size() >= FLUSH_INTERVAL) {
				flush();
			}
		}

		synchronized void flush() {
			persistCollection(entities);
			entities.clear();
		}

		abstract void persistCollection(Collection<E> entities);

	}

}
