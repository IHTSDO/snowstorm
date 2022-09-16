package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import com.google.common.collect.Iterables;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRProperty;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRConceptService {

	private static final int SAVE_BATCH_SIZE = 500;
	private static final int DELETE_BATCH_SIZE = 1_000;
	public static final String PARENT = "parent";
	public static final String CHILD = "child";

	@Autowired
	private FHIRConceptRepository conceptRepository;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void saveAllConceptsOfCodeSystemVersion(TermCodeSystemVersion termCodeSystemVersion, FHIRCodeSystemVersion codeSystemVersion) {

		// Some code systems only return the top level concepts with getConcepts()
		// Need to traverse down to gather ancestors for a full list of concepts to save.
		Set<TermConcept> allTermConcepts = new HashSet<>(termCodeSystemVersion.getConcepts());
		Set<TermConcept> gatheredChildren = new HashSet<>();
		for (TermConcept concept : allTermConcepts) {
			gatherChildCodes(concept, gatheredChildren);
		}
		allTermConcepts.addAll(gatheredChildren);

		Collection<FHIRConcept> concepts = allTermConcepts.stream()
				.map(termConcept -> new FHIRConcept(termConcept, codeSystemVersion))
				.collect(Collectors.toList());

		saveAllConceptsOfCodeSystemVersion(codeSystemVersion, codeSystemVersion.getId(), concepts);
	}

	public void saveAllConceptsOfCodeSystemVersion(List<CodeSystem.ConceptDefinitionComponent> definitionConcepts, FHIRCodeSystemVersion codeSystemVersion) {

		Set<CodeSystem.ConceptDefinitionComponent> allConcepts = new HashSet<>();
		for (CodeSystem.ConceptDefinitionComponent concept : definitionConcepts) {
			collectChildren(concept, allConcepts);
		}

		List<FHIRConcept> concepts = allConcepts.stream()
				.map(definitionConcept -> new FHIRConcept(definitionConcept, codeSystemVersion))
				.collect(Collectors.toList());
		saveAllConceptsOfCodeSystemVersion(codeSystemVersion, codeSystemVersion.getId(), concepts);
	}

	private void collectChildren(CodeSystem.ConceptDefinitionComponent parent, Set<CodeSystem.ConceptDefinitionComponent> allConcepts) {
		allConcepts.add(parent);
		for (CodeSystem.ConceptDefinitionComponent child : orEmpty(parent.getConcept())) {
			parent.addProperty(new CodeSystem.ConceptPropertyComponent(new CodeType("child"), new CodeType(child.getCode())));
			child.addProperty(new CodeSystem.ConceptPropertyComponent(new CodeType("parent"), new CodeType(parent.getCode())));
			collectChildren(child, allConcepts);
		}
	}

	private void saveAllConceptsOfCodeSystemVersion(FHIRCodeSystemVersion codeSystemVersion, String idWithVersion, Collection<FHIRConcept> concepts) {
		deleteExistingCodes(idWithVersion);

		if (concepts.isEmpty()) {
			return;
		}

		FHIRGraphBuilder graphBuilder = new FHIRGraphBuilder();
		if ("is-a".equals(codeSystemVersion.getHierarchyMeaning())) {
			// Record transitive closure of concepts for subsumption testing
			for (FHIRConcept concept : concepts) {
				for (String parentCode : concept.getParents()) {
					graphBuilder.addParent(concept.getCode(), parentCode);
				}
			}
			// Add parent and child properties if missing
			Map<String, String> conceptDisplayMap = concepts.stream()
					.filter(concept -> concept.getDisplay() != null)
					.collect(Collectors.toMap(FHIRConcept::getCode, FHIRConcept::getDisplay));
			for (FHIRConcept concept : concepts) {
				Map<String, List<FHIRProperty>> properties = concept.getProperties();
				Collection<String> parents = graphBuilder.getNodeParents(concept.getCode());
				if (!properties.containsKey(PARENT)) {
					properties.put(PARENT, parents.stream().map(parent -> new FHIRProperty(PARENT, conceptDisplayMap.get(parent), parent, "CODING"))
							.collect(Collectors.toList()));
				}
				Collection<String> children = graphBuilder.getNodeChildren(concept.getCode());
				if (!properties.containsKey(CHILD)) {
					properties.put(CHILD, children.stream().map(child -> new FHIRProperty(CHILD, conceptDisplayMap.get(child), child, "CODING"))
							.collect(Collectors.toList()));
				}
			}
		}

		Set<String> props = new HashSet<>();
		logger.info("Saving {} '{}' fhir concepts.", concepts.size(), idWithVersion);
		float allSize = concepts.size();
		int tenPercent = concepts.size() / 10;
		if (tenPercent == 0) {
			tenPercent = 1;
		}
		Float percentToLog = null;
		int saved = 0;

		for (List<FHIRConcept> conceptsBatch : Iterables.partition(concepts, SAVE_BATCH_SIZE)) {
			List<FHIRConcept> batch = new ArrayList<>();
			for (FHIRConcept concept : conceptsBatch) {
				if (concept.getProperties() != null) {
					props.addAll(concept.getProperties().keySet());
				}
				concept.setAncestors(graphBuilder.getTransitiveClosure(concept.getCode()));
				batch.add(concept);
				saved++;
				if (saved % tenPercent == 0f) {
					percentToLog = (saved / allSize) * 100;
				}
			}
			conceptRepository.saveAll(batch);
			if (concepts.size() > 1000 && percentToLog != null) {
				logger.info("Saved {}% of '{}' fhir concepts.", Math.round(percentToLog), idWithVersion);
				percentToLog = null;
			}
		}
		System.out.print("All properties: ");
		System.out.println(props);
		System.out.println();
	}

	public void deleteExistingCodes(String idWithVersion) {
		Page<FHIRConcept> existingConcepts = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, 1));
		long totalExisting = existingConcepts.getTotalElements();
		if (totalExisting > 0) {
			logger.info("Deleting {} existing concepts for this code system version {}", totalExisting, idWithVersion);
			// Deleting by query often seems to exceed the default 30 second query timeout so we will page through them...
			Page<FHIRConcept> codesToDelete = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			while (!codesToDelete.isEmpty()) {
				conceptRepository.deleteByCodeSystemVersionAndCodeIn(idWithVersion, codesToDelete.getContent().stream().map(FHIRConcept::getCode).collect(Collectors.toList()));
				codesToDelete = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			}
			System.out.println();
			logger.info("Existing codes deleted");
		}
	}

	private void gatherChildCodes(TermConcept concept, Set<TermConcept> allConcepts) {
		allConcepts.addAll(concept.getChildCodes());
		for (TermConcept childCode : concept.getChildCodes()) {
			gatherChildCodes(childCode, allConcepts);
		}
	}

	public FHIRConcept findConcept(FHIRCodeSystemVersion systemVersion, String code) {
		return conceptRepository.findFirstByCodeSystemVersionAndCode(systemVersion.getId(), code);
	}

	public Page<FHIRConcept> findConcepts(BoolQueryBuilder fhirConceptQuery, PageRequest pageRequest) {
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(fhirConceptQuery)
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		return toPage(elasticsearchTemplate.search(searchQuery, FHIRConcept.class), pageRequest);
	}

	public Page<FHIRConcept> findConcepts(Set<String> codes, FHIRCodeSystemVersion codeSystemVersion, Pageable pageable) {
		return conceptRepository.findByCodeSystemVersionAndCodeIn(codeSystemVersion.getId(), codes, pageable);
	}
}
