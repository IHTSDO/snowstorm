package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Commit;
import com.kaicube.snomed.elasticsnomed.domain.Concepts;
import com.kaicube.snomed.elasticsnomed.domain.QueryIndexConcept;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import com.kaicube.snomed.elasticsnomed.repositories.QueryIndexConceptRepository;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.ihtsdo.otf.snomedboot.ComponentStore;
import org.ihtsdo.otf.snomedboot.domain.Concept;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentFactoryImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ConceptImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class QueryIndexService {

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryIndexConceptRepository queryIndexConceptRepository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Set<Long> retrieveAncestors(String conceptId, String path) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(path))
						.must(termQuery("conceptId", conceptId))
				)
				.build();
		final List<QueryIndexConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryIndexConcept.class).getContent();
		if (concepts.size() > 1) {
			logger.error("More than one index concept found {}", concepts);
			throw new IllegalStateException("More than one query-index-concept found for id " + conceptId + " on branch " + path + ".");
		}
		return concepts.isEmpty() ? null : concepts.get(0).getAncestors();
	}

	public void createTransitiveClosureForEveryConcept(Commit commit) {
		logger.info("Calculating transitive closures");
		Pageable pageable = new PageRequest(0, 10000);
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteriaWithinOpenCommit(commit))
						.must(termQuery("typeId", Concepts.ISA))
				)
				.withSort(new FieldSortBuilder("effectiveTime"))
				.withPageable(pageable);

		final ComponentStore componentStore = new ComponentStore();
		ComponentFactory componentFactory = new ComponentFactoryImpl(componentStore);

		Page<Relationship> relationships;
		do {
			relationships = elasticsearchTemplate.queryForPage(queryBuilder.build(), Relationship.class);
			for (Relationship relationship : relationships) {
				if (relationship.isActive()) {
					componentFactory.addConceptParent(relationship.getSourceId(), relationship.getDestinationId());
				} else {
					componentFactory.removeConceptParent(relationship.getSourceId(), relationship.getDestinationId());
				}
			}
			pageable = pageable.next();
			queryBuilder.withPageable(pageable);
		} while (!relationships.isLast());
		logger.info("Processing {} relationships.", relationships.getTotalElements());

		final ObjectCollection<ConceptImpl> concepts = componentStore.getConcepts().values();
		final List<QueryIndexConcept> conceptsToSave = new ArrayList<>();
		Concept concept;
		int i = 0;
		final ObjectIterator<ConceptImpl> iterator = concepts.iterator();
		while (iterator.hasNext()) {
			concept = iterator.next();
			conceptsToSave.add(new QueryIndexConcept(concept.getId(), concept.getAncestorIds()));
			i++;
			if (i % 1000 == 0) {
				doSaveBatch(conceptsToSave, commit);
				conceptsToSave.clear();
			}
		}
		doSaveBatch(conceptsToSave, commit);
	}

	public void doSaveBatch(Collection<QueryIndexConcept> indexConcepts, Commit commit) {
		if (!indexConcepts.isEmpty()) {
			logger.info("Saving batch of {} query-index-concepts", indexConcepts.size());
			final List<Long> ids = indexConcepts.stream().map(QueryIndexConcept::getConceptId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, "conceptId", QueryIndexConcept.class, ids, this.queryIndexConceptRepository);
			versionControlHelper.setEntityMeta(indexConcepts, commit);
			queryIndexConceptRepository.save(indexConcepts);
		}
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}
}
