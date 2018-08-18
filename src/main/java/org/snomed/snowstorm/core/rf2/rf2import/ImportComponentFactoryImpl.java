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
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class ImportComponentFactoryImpl extends ImpotentComponentFactory {

	private static final int FLUSH_INTERVAL = 5000;
	private static final int MEMBER_ADDITIONAL_FIELD_OFFSET = 6;
	private static final Pattern EFFECTIVE_DATE_PATTERN = Pattern.compile("\\d{8}");

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

	boolean coreComponentsFlushed;

	ImportComponentFactoryImpl(ConceptService conceptService, ReferenceSetMemberService memberService, BranchService branchService, String path) {
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
					maxEffectiveTimeCollector.add(component.getEffectiveTimeI());
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
					maxEffectiveTimeCollector.add(component.getEffectiveTimeI());
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
					maxEffectiveTimeCollector.add(component.getEffectiveTimeI());
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
					maxEffectiveTimeCollector.add(component.getEffectiveTimeI());
				});
				memberService.doSaveBatchMembers(entities, commit);
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
		Integer effectiveTimeI = getEffectiveTimeI(effectiveTime);
		final Concept concept = new Concept(conceptId, effectiveTimeI, isActive(active), moduleId, definitionStatusId);
		if (effectiveTimeI != null) {
			concept.release(effectiveTimeI);
		}
		conceptPersistBuffer.save(concept);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId,
			String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {

		Integer effectiveTimeI = getEffectiveTimeI(effectiveTime);
		final Relationship relationship = new Relationship(id, effectiveTimeI, isActive(active), moduleId, sourceId,
				destinationId, Integer.parseInt(relationshipGroup), typeId, characteristicTypeId, modifierId);
		if (effectiveTime != null) {
			relationship.release(effectiveTimeI);
		}
		relationshipPersistBuffer.save(relationship);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode,
			String typeId, String term, String caseSignificanceId) {

		Integer effectiveTimeI = getEffectiveTimeI(effectiveTime);
		final Description description = new Description(id, effectiveTimeI, isActive(active), moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
		if (effectiveTimeI != null) {
			description.release(effectiveTimeI);
		}
		descriptionPersistBuffer.save(description);
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId,
			String referencedComponentId, String... otherValues) {

		Integer effectiveTimeI = getEffectiveTimeI(effectiveTime);
		ReferenceSetMember member = new ReferenceSetMember(id, effectiveTimeI, isActive(active), moduleId, refsetId, referencedComponentId);
		for (int i = MEMBER_ADDITIONAL_FIELD_OFFSET; i < fieldNames.length; i++) {
			member.setAdditionalField(fieldNames[i], otherValues[i - MEMBER_ADDITIONAL_FIELD_OFFSET]);
		}
		if (effectiveTime != null) {
			member.release(effectiveTimeI);
		}
		memberPersistBuffer.save(member);
	}

	private Integer getEffectiveTimeI(String effectiveTime) {
		return effectiveTime != null && !effectiveTime.isEmpty() && EFFECTIVE_DATE_PATTERN.matcher(effectiveTime).matches() ? Integer.parseInt(effectiveTime) : null;
	}

	Integer getMaxEffectiveTime() {
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
