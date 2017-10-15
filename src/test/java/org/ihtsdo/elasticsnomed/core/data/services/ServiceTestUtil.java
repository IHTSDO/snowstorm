package org.ihtsdo.elasticsnomed.core.data.services;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceTestUtil {

	public static final PageRequest PAGE_REQUEST = new PageRequest(0, 100);

	private ConceptService conceptService;

	public ServiceTestUtil(ConceptService conceptService) {
		this.conceptService = conceptService;
	}

	public static Set<Long> toLongSet(String... longString) {
		return Arrays.stream(longString).map(Long::parseLong).collect(Collectors.toSet());
	}

	public void createConceptWithPathIdAndTerms(String path, String conceptId, String... terms) throws ServiceException {
		final Concept concept = new Concept(conceptId);
		for (String term : terms) {
			concept.addDescription(new Description(term));
		}
		conceptService.create(concept, path);
	}
}
