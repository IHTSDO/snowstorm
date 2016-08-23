package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.elasticversioncontrol.api.ComponentService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Commit;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class QueryIndexService extends ComponentService {

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

		try (final CloseableIterator<Relationship> relationshipStream = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			relationshipStream.forEachRemaining(relationship -> {
				if (relationship.isActive()) {
					componentFactory.addConceptParent(relationship.getSourceId(), relationship.getDestinationId());
				} else {
					componentFactory.removeConceptParent(relationship.getSourceId(), relationship.getDestinationId());
				}
			});
		}

		logger.info("Processing relationships.");

		final ObjectCollection<ConceptImpl> concepts = componentStore.getConcepts().values();
		final List<QueryIndexConcept> conceptsToSave = new ArrayList<>();
		Concept concept;
		int i = 0;
		final ObjectIterator<ConceptImpl> iterator = concepts.iterator();
		while (iterator.hasNext()) {
			concept = iterator.next();
			final QueryIndexConcept indexConcept = new QueryIndexConcept(concept.getId(), concept.getAncestorIds());
			indexConcept.setChanged(true);// TODO - Save only if changed.
			conceptsToSave.add(indexConcept);
			i++;
			if (i % 1000 == 0) {
				doSaveBatch(conceptsToSave, commit);
				conceptsToSave.clear();
			}
		}
		doSaveBatch(conceptsToSave, commit);
	}

	public void doSaveBatch(Collection<QueryIndexConcept> indexConcepts, Commit commit) {
		doSaveBatchComponents(indexConcepts, commit, QueryIndexConcept.class, "conceptId", queryIndexConceptRepository);
	}

	public void deleteAll() {
		queryIndexConceptRepository.deleteAll();
	}
}
