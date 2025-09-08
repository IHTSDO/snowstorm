package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.google.common.collect.Iterables;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRProperty;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;
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
	private ElasticsearchOperations elasticsearchOperations;

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
				.toList();

		saveAllConceptsOfCodeSystemVersion(codeSystemVersion, codeSystemVersion.getId(), concepts);
	}

	public void saveAllConceptsOfCodeSystemVersion(List<CodeSystem.ConceptDefinitionComponent> definitionConcepts, FHIRCodeSystemVersion codeSystemVersion) {

		Set<CodeSystem.ConceptDefinitionComponent> allConcepts = new HashSet<>();
		for (CodeSystem.ConceptDefinitionComponent concept : definitionConcepts) {
			collectChildren(concept, allConcepts);
		}

		List<FHIRConcept> concepts = allConcepts.stream()
				.map(definitionConcept -> new FHIRConcept(definitionConcept, codeSystemVersion))
				.toList();
		saveAllConceptsOfCodeSystemVersion(codeSystemVersion, codeSystemVersion.getId(), concepts);
	}

	private void collectChildren(CodeSystem.ConceptDefinitionComponent parent, Set<CodeSystem.ConceptDefinitionComponent> allConcepts) {
		allConcepts.add(parent);
		for (CodeSystem.ConceptDefinitionComponent child : orEmpty(parent.getConcept())) {
			parent.addProperty(new CodeSystem.ConceptPropertyComponent(new CodeType(CHILD), new CodeType(child.getCode())));
			child.addProperty(new CodeSystem.ConceptPropertyComponent(new CodeType(PARENT), new CodeType(parent.getCode())));
			collectChildren(child, allConcepts);
		}
	}

	private void saveAllConceptsOfCodeSystemVersion(FHIRCodeSystemVersion codeSystemVersion, String idWithVersion, Collection<FHIRConcept> concepts) {
		deleteExistingCodes(idWithVersion);

		if (concepts.isEmpty()) {
			return;
		}

		FHIRGraphBuilder graphBuilder = new FHIRGraphBuilder();
		if (Objects.isNull(codeSystemVersion.getHierarchyMeaning()) || "is-a".equals(codeSystemVersion.getHierarchyMeaning())) {
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
				properties.computeIfAbsent(PARENT, k -> parents.stream()
					.map(parent -> new FHIRProperty(PARENT, conceptDisplayMap.get(parent), parent, "CODING"))
					.toList());

				Collection<String> children = graphBuilder.getNodeChildren(concept.getCode());
				properties.computeIfAbsent(CHILD, k -> children.stream()
					.map(child -> new FHIRProperty(CHILD, conceptDisplayMap.get(child), child, "CODING"))
					.toList());
			}
		}

		Set<String> props = new HashSet<>();
		//treat extensions as properties, until better solution...
		concepts.forEach(concept ->
			concept.getExtensions().forEach((key,value)->
				concept.getProperties().put(key,value)));

		concepts.stream()
				.filter(concept -> concept.getProperties() != null)
				.forEach(concept -> props.addAll(concept.getProperties().keySet()));

		logger.info("Saving {} '{}' fhir concepts. All properties: {}", concepts.size(), idWithVersion, props);
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
	}

	public Page<FHIRConcept> findConcepts(String idWithVersion, PageRequest pageRequest){
		return conceptRepository.findByCodeSystemVersion(idWithVersion, pageRequest);
	}

	public void deleteExistingCodes(String idWithVersion) {
		Page<FHIRConcept> existingConcepts = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, 1));
		long totalExisting = existingConcepts.getTotalElements();
		if (totalExisting > 0) {
			logger.info("Deleting {} existing concepts for code system version: {}", totalExisting, idWithVersion);
			// Deleting by query often seems to exceed the default 30 second query timeout so we will page through them...
			Page<FHIRConcept> codesToDelete = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			while (!codesToDelete.isEmpty()) {
				conceptRepository.deleteByCodeSystemVersionAndCodeIn(idWithVersion, codesToDelete.getContent().stream().map(FHIRConcept::getCode).toList());
				codesToDelete = conceptRepository.findByCodeSystemVersion(idWithVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			}
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

	public Page<FHIRConcept> findConcepts(BoolQuery.Builder fhirConceptQuery, PageRequest pageRequest) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(fhirConceptQuery.build()._toQuery())
				.withSort(Sort.by(FHIRConcept.Fields.DISPLAY_LENGTH, FHIRConcept.Fields.CODE))
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		updateQueryWithSearchAfter(searchQuery, pageRequest);
		logger.info("QUERY: {}", searchQuery.getQuery());
		return toPage(elasticsearchOperations.search(searchQuery, FHIRConcept.class), pageRequest);
	}

	public SearchAfterPage<String> findConceptCodes(BoolQuery fhirConceptQuery, PageRequest pageRequest) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(fhirConceptQuery._toQuery())
				.withSort(Sort.by(FHIRConcept.Fields.CODE))
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		updateQueryWithSearchAfter(searchQuery, pageRequest);
		SearchHits<FHIRConcept> searchHits = elasticsearchOperations.search(searchQuery, FHIRConcept.class);
		return PageHelper.toSearchAfterPage(searchHits, FHIRConcept::getCode, pageRequest);
	}


	public Page<FHIRConcept> findConcepts(Set<String> codes, FHIRCodeSystemVersion codeSystemVersion, Pageable pageable) {
		return conceptRepository.findByCodeSystemVersionAndCodeIn(codeSystemVersion.getId(), codes, pageable);
	}

	public Page<FHIRConcept> findConceptsWithoutSystem(String code, PageRequest pageRequest) {
		BoolQuery.Builder bool = new BoolQuery.Builder();
		bool.must(new TermQuery.Builder().value(code).field("code").build()._toQuery());

		return findConcepts(bool,pageRequest );

	}
}
