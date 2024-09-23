package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.IdentifierRepository;
import org.snomed.snowstorm.core.data.services.pojo.IdentifierSearchRequest;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;

@Service
public class IdentifierComponentService extends ComponentService {

	@Autowired
	private IdentifierRepository identifierRepository;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private BranchService branchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final Function <Identifier, Object[]> IDENTIFIER_ID_SEARCH_AFTER_EXTRACTOR = identifier -> {
		if (identifier == null) {
			return null;
		}

		String id = identifier.getId();
		return id == null ? null : SearchAfterHelper.convertToTokenAndBack(new Object[]{id});
	};


	/**
	 * Persists identifiers updates within commit.
	 * Inactive identifiers which have not been released will be deleted
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<Identifier> doSaveBatchIdentifiers(Collection<Identifier> identifiers, Commit commit) {
		// Delete inactive unreleased identifiers
		identifiers.stream()
				.filter(member -> !member.isActive() && !member.isReleased())
				.forEach(Identifier::markDeleted);

		identifiers.forEach(Identifier::updateEffectiveTime);

		return doSaveBatchComponents(identifiers, commit, Identifier.Fields.INTERNAL_IDENTIFIER_ID, identifierRepository);
	}

	public Page<Identifier> findIdentifiers(String branch, IdentifierSearchRequest searchRequest, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		return findIdentifiers(branchCriteria, searchRequest, pageRequest);
	}

	public Page<Identifier> findIdentifiers( BranchCriteria branchCriteria, IdentifierSearchRequest searchRequest, PageRequest pageRequest) {
		NativeQuery query = new NativeQueryBuilder().withQuery(buildIdentifierQuery(searchRequest, branchCriteria)).withPageable(pageRequest).build();
		query.setTrackTotalHits(true);
		updateQueryWithSearchAfter(query, pageRequest);
		SearchHits<Identifier> searchHits = elasticsearchOperations.search(query, Identifier.class);
		PageImpl<Identifier> identifiers = new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
		return PageHelper.toSearchAfterPage(identifiers, IDENTIFIER_ID_SEARCH_AFTER_EXTRACTOR);

	}

	public Identifier createIdentifier(String branch, Identifier identifier) {
		Iterator<Identifier> identifiers = createIdentifiers(branch, Collections.singleton(identifier)).iterator();
		return identifiers.hasNext() ? identifiers.next() : null;
	}

	public Iterable<Identifier> createIdentifiers(String branch, Set<Identifier> identifiers) {
		try (final Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata(String.format("Creating %s identifiers.", identifiers.size())))) {

			// Grab branch metadata including values inherited from ancestor branches
			final Metadata metadata = branchService.findBranchOrThrow(commit.getBranch().getPath(), true).getMetadata();
			String defaultModuleId = metadata.getString(Config.DEFAULT_MODULE_ID_KEY);
			identifiers.forEach(identifier -> {
				if (identifier.getInternalIdentifierId() == null) {
					identifier.setInternalIdentifierId(identifier.getAlternateIdentifier() + "-" + identifier.getIdentifierSchemeId());
				}
				if (identifier.getModuleId() == null) {
					identifier.setModuleId(StringUtils.hasLength(defaultModuleId) ? defaultModuleId : Concepts.CORE_MODULE);
				}
				identifier.markChanged();
			});
			final Iterable<Identifier> savedIdentifiers = doSaveBatchIdentifiers(identifiers, commit);
			commit.markSuccessful();
			return savedIdentifiers;
		}
	}

	public void deleteAll() {
		identifierRepository.deleteAll();
	}

	private Query buildIdentifierQuery(IdentifierSearchRequest searchRequest, BranchCriteria branchCriteria) {
		BoolQuery.Builder queryBuilder = bool().must(branchCriteria.getEntityBranchCriteria(Identifier.class));

		if (searchRequest.getActive() != null) {
			queryBuilder.must(termQuery(Identifier.Fields.ACTIVE, searchRequest.getActive()));
		}

		if (searchRequest.isNullEffectiveTime() != null) {
			if (searchRequest.isNullEffectiveTime()) {
				queryBuilder.mustNot(existsQuery(Identifier.Fields.EFFECTIVE_TIME));
			} else {
				queryBuilder.must(existsQuery(Identifier.Fields.EFFECTIVE_TIME));
			}
		}

		if (searchRequest.getAlternateIdentifier() != null) {
			queryBuilder.must(termQuery(Identifier.Fields.ALTERNATE_IDENTIFIER, searchRequest.getAlternateIdentifier()));
		}

		if (searchRequest.getIdentifierSchemeId() != null) {
			queryBuilder.must(termQuery(Identifier.Fields.IDENTIFIER_SCHEME_ID, searchRequest.getIdentifierSchemeId()));
		}


		String module = searchRequest.getModule();
		if (!Strings.isNullOrEmpty(module)) {
			queryBuilder.must(termQuery(Identifier.Fields.MODULE_ID, module));
		}

		Collection<? extends Serializable> referencedComponentIds = searchRequest.getReferencedComponentIds();
		if (referencedComponentIds != null && referencedComponentIds.size() > 0) {
			queryBuilder.must(termsQuery(Identifier.Fields.REFERENCED_COMPONENT_ID, referencedComponentIds));
		}

		return queryBuilder.build()._toQuery();
	}
	void joinIdentifiers(BranchCriteria branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap, List<LanguageDialect> languageDialects, TimerUtil timer) {
		final NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

		final Set<String> allConceptIds = new HashSet<>();
		if (conceptIdMap != null) {
			allConceptIds.addAll(conceptIdMap.keySet());
		}
		if (allConceptIds.isEmpty()) {
			return;
		}

		// Fetch Identifier
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(bool(b -> b
					.must(branchCriteria.getEntityBranchCriteria(Identifier.class))
					.must(termQuery(Identifier.Fields.ACTIVE, true))
					.must(termsQuery(Identifier.Fields.REFERENCED_COMPONENT_ID, conceptIds))))
					.withPageable(LARGE_PAGE);
			try (final SearchHitsIterator<Identifier> identifiers = elasticsearchOperations.searchForStream(queryBuilder.build(), Identifier.class)) {
				identifiers.forEachRemaining(hit -> {
					Identifier identifier = hit.getContent();
					identifier.setIdentifierScheme(getConceptMini(conceptMiniMap, identifier.getIdentifierSchemeId(), languageDialects));

					// Join Identifiers to concepts for loading whole concepts use case.
					final String referencedComponentId = identifier.getReferencedComponentId();
					if (conceptIdMap != null) {
						final Concept concept = conceptIdMap.get(referencedComponentId);
						if (concept != null) {
							concept.addIdentifier(identifier);
						}
					}
				});
			}
		}
		if (timer != null) timer.checkpoint("get Identifier " + getFetchCount(allConceptIds.size()));
	}

	private static ConceptMini getConceptMini(Map<String, ConceptMini> conceptMiniMap, String id, List<LanguageDialect> languageDialects) {
		if (id == null) return new ConceptMini((String)null, languageDialects);
		return conceptMiniMap.computeIfAbsent(id, i -> new ConceptMini(id, languageDialects));
	}
}
