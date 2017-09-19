package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierService;
import org.ihtsdo.elasticsnomed.core.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class DescriptionService extends ComponentService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Set<Description> fetchDescriptions(String branchPath, Set<String> conceptIds) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<String, Concept> conceptMap = new HashMap<>();
		for (String conceptId : conceptIds) {
			conceptMap.put(conceptId, new Concept(conceptId));
		}
		fetchDescriptions(branchCriteria, conceptMap, null, null, false);
		return conceptMap.values().stream().flatMap(c -> c.getDescriptions().stream()).collect(Collectors.toSet());
	}

	void fetchDescriptions(QueryBuilder branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap,
								   TimerUtil timer, boolean fetchInactivationInfo) {

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		final Set<String> allConceptIds = new HashSet<>();
		if (conceptIdMap != null) {
			allConceptIds.addAll(conceptIdMap.keySet());
		}
		if (conceptMiniMap != null) {
			allConceptIds.addAll(conceptMiniMap.keySet());
		}
		if (allConceptIds.isEmpty()) {
			return;
		}

		// Fetch Descriptions
		Map<String, Description> descriptionIdMap = new HashMap<>();
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(branchCriteria)
					.must(termsQuery("conceptId", conceptIds)))
					.withPageable(LARGE_PAGE);
			try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(queryBuilder.build(), Description.class)) {
				descriptions.forEachRemaining(description -> {
					// Workaround - transient property sometimes persisted? FIXME
					description.setInactivationIndicator(null);

					// Join Descriptions
					final String descriptionConceptId = description.getConceptId();
					if (conceptIdMap != null) {
						final Concept concept = conceptIdMap.get(descriptionConceptId);
						if (concept != null) {
							concept.addDescription(description);
						}
					}
					if (conceptMiniMap != null) {
						final ConceptMini conceptMini = conceptMiniMap.get(descriptionConceptId);
						if (conceptMini != null && Concepts.FSN.equals(description.getTypeId()) && description.isActive()) {
							conceptMini.addActiveFsn(description);
						}
					}

					// Store Descriptions for Lang Refset join
					descriptionIdMap.put(description.getDescriptionId(), description);
				});
			}
		}
		if (timer != null) timer.checkpoint("get descriptions " + getFetchCount(allConceptIds.size()));

		// Fetch Inactivation Indicators and Associations
		if (fetchInactivationInfo) {
			Set<String> componentIds;
			if (conceptIdMap != null) {
				componentIds = Sets.union(conceptIdMap.keySet(), descriptionIdMap.keySet());
			} else {
				componentIds = descriptionIdMap.keySet();
			}
			for (List<String> componentIdsSegment : Iterables.partition(componentIds, CLAUSE_LIMIT)) {
				queryBuilder.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery("refsetId", Concepts.inactivationAndAssociationRefsets))
						.must(termsQuery("referencedComponentId", componentIdsSegment)))
						.withPageable(LARGE_PAGE);
				// Join Members
				try (final CloseableIterator<ReferenceSetMember> members = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
					members.forEachRemaining(member -> {
						String referencedComponentId = member.getReferencedComponentId();
						switch (member.getRefsetId()) {
							case Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET:
								conceptIdMap.get(referencedComponentId).setInactivationIndicatorMember(member);
								break;
							case Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET:
								descriptionIdMap.get(referencedComponentId).setInactivationIndicatorMember(member);
								break;
							default:
								if (IdentifierService.isConceptId(referencedComponentId)) {
									Concept concept = conceptIdMap.get(referencedComponentId);
									if (concept != null) {
										concept.addAssociationTargetMember(member);
									} else {
										logger.warn("Association ReferenceSetMember {} references concept {} " +
												"which is not in scope.", member.getId(), referencedComponentId);
									}
								} else if (IdentifierService.isDescriptionId(referencedComponentId)) {
									Description description = descriptionIdMap.get(referencedComponentId);
									if (description != null) {
										description.addAssociationTargetMember(member);
									} else {
										logger.warn("Association ReferenceSetMember {} references concept {} " +
												"which is not in scope.", member.getId(), referencedComponentId);
									}
								} else {
									logger.error("Association ReferenceSetMember {} references unexpected component type {}", member.getId(), referencedComponentId);
								}
								break;
						}
					});
				}
			}
			if (timer != null) timer.checkpoint("get inactivation refset " + getFetchCount(componentIds.size()));
		}

		// Fetch Lang Refset Members
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(branchCriteria)
					.must(termsQuery("refsetId", Concepts.US_EN_LANG_REFSET, Concepts.GB_EN_LANG_REFSET)) // TODO: Replace with ECL selection
					.must(termsQuery("conceptId", conceptIds)))
					.withPageable(LARGE_PAGE);
			// Join Lang Refset Members
			try (final CloseableIterator<ReferenceSetMember> langRefsetMembers = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
				langRefsetMembers.forEachRemaining(langRefsetMember -> {
					Description description = descriptionIdMap.get(langRefsetMember.getReferencedComponentId());
						if (description != null) {
							description.addLanguageRefsetMember(langRefsetMember);
						} else {
							logger.error("Description {} for lang refset member {} not found on branch {}!",
									langRefsetMember.getReferencedComponentId(), langRefsetMember.getMemberId(), branchCriteria.toString().replace("\n", ""));
						}
					});
			}
		}
		if (timer != null) timer.checkpoint("get lang refset " + getFetchCount(allConceptIds.size()));
	}

	public Page<Description> findDescriptions(String path, String term, PageRequest pageRequest) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery();
		builder.must(branchCriteria);
		addTermClauses(term, builder);

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withPageable(pageRequest);

		return elasticsearchTemplate.queryForPage(addTermSort(queryBuilder.build()), Description.class);
	}

	protected static BoolQueryBuilder addTermClauses(String term, BoolQueryBuilder boolBuilder) {
		if (IdentifierService.isConceptId(term)) {
			boolBuilder.must(termQuery("conceptId", term));
		} else {
			if (!Strings.isNullOrEmpty(term)) {
				String[] split = term.split(" ");
				for (String word : split) {
					word = word.trim();
					if (!word.isEmpty()) {
						if (!word.contains("*")) {
							word += "*";
						}
						boolBuilder.must(simpleQueryStringQuery(word).field("term"));
					}
				}
			}
		}
		return boolBuilder;
	}

	protected static NativeSearchQuery addTermSort(NativeSearchQuery query) {
		query.addSort(new Sort("termLen"));
		return query;
	}

	public Description fetchDescription(String path, String descriptionId) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		BoolQueryBuilder query = boolQuery().must(branchCriteria)
				.must(termsQuery("descriptionId", descriptionId));
		List<Description> descriptions = elasticsearchTemplate.queryForList(
				new NativeSearchQueryBuilder().withQuery(query).build(), Description.class);
		if (!descriptions.isEmpty()) {
			return descriptions.get(0);
		}
		return null;
	}
}
