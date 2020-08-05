package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.ihtsdo.drools.domain.Relationship;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class ConceptDroolsValidationService implements org.ihtsdo.drools.service.ConceptService {

	private final String branchPath;
	private final BranchCriteria branchCriteria;
	private final ElasticsearchOperations elasticsearchTemplate;
	private final QueryService queryService;

	ConceptDroolsValidationService(String branchPath, BranchCriteria branchCriteria, ElasticsearchOperations elasticsearchTemplate, QueryService queryService) {
		this.branchPath = branchPath;
		this.branchCriteria = branchCriteria;
		this.elasticsearchTemplate = elasticsearchTemplate;
		this.queryService = queryService;
	}

	@Override
	public boolean isActive(String conceptId) {
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery(Concept.Fields.ACTIVE, true)))
				.withPageable(Config.PAGE_OF_ONE)
				.build();
		List<Concept> matches = elasticsearchTemplate.queryForList(query, Concept.class);
		return !matches.isEmpty();
	}

    @Override
    public org.ihtsdo.drools.domain.Concept findById(String conceptId) {
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(boolQuery()
                        .must(branchCriteria.getEntityBranchCriteria(Concept.class))
                        .must(termQuery(Concept.Fields.CONCEPT_ID, conceptId)))
                .withPageable(Config.PAGE_OF_ONE)
                .build();
        List<Concept> matches = elasticsearchTemplate.queryForList(query, Concept.class);
        return !matches.isEmpty() ? new DroolsConcept(matches.get(0)) : null;
    }

    @Override
	public Set<String> getAllTopLevelHierarchies() {
		return getConceptIdsByEcl(false, "<!" + Concepts.SNOMEDCT_ROOT);
	}

	@Override
	public Set<String> findStatedAncestorsOfConcept(org.ihtsdo.drools.domain.Concept concept) {
		// This could be an unsaved concept, don't use the concept id, collect the stated parents - they will have an SCTID.

		Set<String> statedParents = getStatedParents(concept);
		if (statedParents.isEmpty()) {
			return Collections.emptySet();
		}

		StringBuilder ecl = new StringBuilder();
		Iterator<String> iterator = statedParents.iterator();
		for (int i = 0; i < statedParents.size(); i++) {
			if (i > 0) {
				ecl.append(" OR ");
			}
			ecl.append(">>").append(iterator.next());// Include self because this ID is a parent.
		}

		return getConceptIdsByEcl(true, ecl.toString());
	}

	@Override
	public Set<String> findTopLevelHierarchiesOfConcept(org.ihtsdo.drools.domain.Concept concept) {
		Set<String> statedParents = getStatedParents(concept);
		if (statedParents.isEmpty()) {
			return Collections.emptySet();
		}

		StringBuilder ecl = new StringBuilder("<!" + Concepts.SNOMEDCT_ROOT + " AND ");
		Iterator<String> iterator = statedParents.iterator();
		if (statedParents.size() > 1) {
			ecl.append("(");
		}
		for (int i = 0; i < statedParents.size(); i++) {
			if (i > 0) {
				ecl.append(" OR ");
			}
			ecl.append(">>").append(iterator.next());// Include self because this ID is a parent.
		}
		if (statedParents.size() > 1) {
			ecl.append(")");
		}

		return getConceptIdsByEcl(false, ecl.toString());
	}

	@Override
	public Set<String> findStatedAncestorsOfConcepts(List<String> statedParentIds) {
		if (statedParentIds.isEmpty()) {
			return Collections.emptySet();
		}
		StringBuilder eclBuilder = new StringBuilder("<" + Concepts.SNOMEDCT_ROOT + " AND ");
		if (statedParentIds.size() > 1) {
			eclBuilder.append("(");
		}
		for (int i = 0; i < statedParentIds.size(); i++) {
			if (i > 0) {
				eclBuilder.append(" OR ");
			}
			eclBuilder.append(">").append(statedParentIds.get(i));
		}
		if (statedParentIds.size() > 1) {
			eclBuilder.append(")");
		}
		Page<Long> idPage = queryService.searchForIds(queryService.createQueryBuilder(true).ecl(eclBuilder.toString()), branchPath, LARGE_PAGE);
		return idPage.getContent().stream().map(Object::toString).collect(Collectors.toSet());
	}

	private Set<String> getStatedParents(org.ihtsdo.drools.domain.Concept concept) {
		return concept.getRelationships().stream()
				.filter(r -> r.isActive() &&
						!r.isAxiomGCI() &&
						Concepts.ISA.equals(r.getTypeId()) &&
						(r.getAxiomId() != null || r.getCharacteristicTypeId().equals(Concepts.STATED_RELATIONSHIP))
				)
				.map(Relationship::getDestinationId)
				.collect(Collectors.toSet());
	}

	private Set<String> getConceptIdsByEcl(boolean stated, String ecl) {
		try {
			Page<ConceptMini> directDescendantsOfRoot = queryService.search(
					queryService.createQueryBuilder(stated).ecl(ecl),
					branchPath, PageRequest.of(0, 1000));
			return directDescendantsOfRoot.getContent().stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
		} catch (IllegalArgumentException e) {
			return Collections.emptySet();
		}
	}
}
