package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptProperty;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FHIRConceptService {

	private static final int SAVE_BATCH_SIZE = 500;

	@Autowired
	private FHIRConceptRepository conceptRepository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void save(TermCodeSystemVersion termCodeSystemVersion, FHIRCodeSystemVersion codeSystemVersion) {
		if (termCodeSystemVersion.getConcepts().isEmpty()) {
			return;
		}
		List<TermConcept> allConcepts = new ArrayList<>(termCodeSystemVersion.getConcepts());

		List<TermConcept> gatheredChildren = new ArrayList<>();
		for (TermConcept concept : allConcepts) {
			gatherChildCodes(concept, gatheredChildren);
		}
		allConcepts.addAll(gatheredChildren);

		Set<String> props = new HashSet<>();
		logger.info("Saving {} '{}' fhir concepts.", allConcepts.size(), codeSystemVersion.getIdAndVersion());
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
				batch.add(new FHIRConcept(termConcept, codeSystemVersion));
				saved++;
				if (saved % tenPercent == 0f) {
					percentToLog = (saved / allSize) * 100;
				}
			}
			conceptRepository.saveAll(batch);
			if (percentToLog != null) {
				logger.info("Saved {}% of '{}' fhir concepts.", Math.round(percentToLog), codeSystemVersion.getIdAndVersion());
				percentToLog = null;
			}
		}
		System.out.println("All props");
		System.out.println(props);
		System.out.println();
	}

	private void gatherChildCodes(TermConcept concept, List<TermConcept> allConcepts) {
		allConcepts.addAll(concept.getChildCodes());
		for (TermConcept childCode : concept.getChildCodes()) {
			gatherChildCodes(childCode, allConcepts);
		}
	}

	public FHIRConcept findConcept(String system, String code) {
//		return conceptRepository.findFirstByCode(code);
		return conceptRepository.findFirstByCodeSystemVersionAndCode(system, code);
	}
}
