package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import com.google.common.collect.Iterables;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRConceptService {

	private static final int SAVE_BATCH_SIZE = 500;
	private static final int DELETE_BATCH_SIZE = 1_000;

	@Autowired
	private FHIRConceptRepository conceptRepository;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void saveAllConceptsOfCodeSystemVersion(TermCodeSystemVersion termCodeSystemVersion, FHIRCodeSystemVersion codeSystemVersion) {

		// First remove any existing codes for this code system version
		String idAndVersion = codeSystemVersion.getId();
		Page<FHIRConcept> existingConcepts = conceptRepository.findByCodeSystemVersion(idAndVersion, PageRequest.of(0, 1));
		long totalExisting = existingConcepts.getTotalElements();
		logger.info("Found {} existing concepts for this code system version {}", totalExisting, idAndVersion);
		if (totalExisting > 0) {
			logger.info("Deleting existing codes...");
			// Deleting by query often seems to exceed the default 30 second query timeout so we will page through them...
			Page<FHIRConcept> codesToDelete = conceptRepository.findByCodeSystemVersion(idAndVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			while (!codesToDelete.isEmpty()) {
				conceptRepository.deleteByCodeSystemVersionAndCodeIn(idAndVersion, codesToDelete.getContent().stream().map(FHIRConcept::getCode).collect(Collectors.toList()));
				codesToDelete = conceptRepository.findByCodeSystemVersion(idAndVersion, PageRequest.of(0, DELETE_BATCH_SIZE));
			}
			System.out.println();
			logger.info("Existing codes deleted");
		}

		if (termCodeSystemVersion.getConcepts().isEmpty()) {
			return;
		}
		Set<TermConcept> allConcepts = new HashSet<>(termCodeSystemVersion.getConcepts());

		// Some code systems only return the top level concepts with getConcepts()
		// Need to traverse down to gather ancestors for a full list of concepts to save.
		Set<TermConcept> gatheredChildren = new HashSet<>();
		for (TermConcept concept : allConcepts) {
			gatherChildCodes(concept, gatheredChildren);
		}
		allConcepts.addAll(gatheredChildren);

		FHIRGraphBuilder graphBuilder = new FHIRGraphBuilder();
		if ("is-a".equals(codeSystemVersion.getHierarchyMeaning())) {
			// Record transitive closure of concepts for subsumption testing
			for (TermConcept concept : allConcepts) {
				for (TermConcept childCode : concept.getChildCodes()) {
					graphBuilder.addParent(childCode.getCode(), concept.getCode());
				}
			}
		}

		Set<String> props = new HashSet<>();
		logger.info("Saving {} '{}' fhir concepts.", allConcepts.size(), idAndVersion);
		float allSize = allConcepts.size();
		int tenPercent = allConcepts.size() / 10;
		if (tenPercent == 0) {
			tenPercent = 1;
		}
		Float percentToLog = null;
		int saved = 0;

		for (List<TermConcept> conceptsBatch : Iterables.partition(allConcepts, SAVE_BATCH_SIZE)) {
			List<FHIRConcept> batch = new ArrayList<>();
			for (TermConcept termConcept : conceptsBatch) {
				for (TermConceptProperty property : termConcept.getProperties()) {
					props.add(property.getKey());
				}
				batch.add(new FHIRConcept(termConcept, codeSystemVersion, graphBuilder.getTransitiveClosure(termConcept.getCode())));
				saved++;
				if (saved % tenPercent == 0f) {
					percentToLog = (saved / allSize) * 100;
				}
			}
			conceptRepository.saveAll(batch);
			if (percentToLog != null) {
				logger.info("Saved {}% of '{}' fhir concepts.", Math.round(percentToLog), idAndVersion);
				percentToLog = null;
			}
		}
		System.out.println("All props");
		System.out.println(props);
		System.out.println();
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
