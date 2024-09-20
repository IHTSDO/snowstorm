package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static co.elastic.clients.json.JsonData.of;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

@Service
public class ConceptChangeHelper {
    private static final Logger logger = LoggerFactory.getLogger(ConceptChangeHelper.class);

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private BranchService branchService;

    @Autowired
    private VersionControlHelper versionControlHelper;

    /**
     * Return Concept identifiers that have been modified on the given Branch between the given time range.
     *
     * @param path           The path of the Branch to find modified Concepts.
     * @param start          The start of the time range to find modified Concepts.
     * @param end            The end of the time range to find modified Concepts.
     * @param sourceIsParent Flag for controlling where and how to apply the time range.
     * @return Concept identifiers that have been modified on the given Branch between the given time range.
     */
    public Set<Long> getConceptsChangedBetweenTimeRange(String path, Date start, Date end, boolean sourceIsParent) {
        logger.info("Creating change report: branch {} time range {} ({}) to {} ({})", path, start.getTime(), start, end.getTime(), end);

        List<Branch> startTimeSlice;
        List<Branch> endTimeSlice;
        if (sourceIsParent) {
            // The source branch is the parent, so we are counting content which could be rebased down.
            // This content can come from any ancestor branch.
            startTimeSlice = versionControlHelper.getTimeSlice(path, start);
            endTimeSlice = versionControlHelper.getTimeSlice(path, end);
        } else {
            // The source branch is the child, so we are counting content which could be promoted up.
            // This content will exist on this path only.
            startTimeSlice = Lists.newArrayList(branchService.findAtTimepointOrThrow(path, start));
            endTimeSlice = Lists.newArrayList(branchService.findAtTimepointOrThrow(path, end));
        }

        if (startTimeSlice.equals(endTimeSlice)) {
            return Collections.emptySet();
        }

        // Find components of each type that are on the target branch and have been ended on the source branch
        final Set<Long> changedConcepts = new LongOpenHashSet();
        final Map<Long, Long> referenceComponentIdToConceptMap = new Long2ObjectOpenHashMap<>();
        final Set<Long> preferredDescriptionIds = new LongOpenHashSet();
        Branch branch = branchService.findBranchOrThrow(path);
        logger.debug("Collecting versions replaced for change report: branch {} time range {} to {}", path, start, end);

        Map<String, Set<String>> changedVersionsReplaced = new HashMap<>();

        // Technique: Search for replaced versions
        // Work out changes in versions replaced between time slices
        Map<String, Set<String>> startVersionsReplaced = versionControlHelper.getAllVersionsReplaced(startTimeSlice);
        Map<String, Set<String>> endVersionsReplaced = versionControlHelper.getAllVersionsReplaced(endTimeSlice);
        for (String type : Sets.union(startVersionsReplaced.keySet(), endVersionsReplaced.keySet())) {
            changedVersionsReplaced.put(type, Sets.difference(
                    endVersionsReplaced.getOrDefault(type, Collections.emptySet()),
                    startVersionsReplaced.getOrDefault(type, Collections.emptySet())));
        }
        if (!changedVersionsReplaced.getOrDefault(Concept.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
            try (final SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(
                    componentsReplacedCriteria(changedVersionsReplaced.get(Concept.class.getSimpleName()), Concept.Fields.CONCEPT_ID).build(), Concept.class)) {
                stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
            }
        }
        if (!changedVersionsReplaced.getOrDefault(Description.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
            NativeQueryBuilder fsnQuery = componentsReplacedCriteria(changedVersionsReplaced.get(Description.class.getSimpleName()), Description.Fields.CONCEPT_ID)
                    .withFilter(termQuery(Description.Fields.TYPE_ID, Concepts.FSN));
            try (final SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(fsnQuery.build(), Description.class)) {
                stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
            }
        }
        if (!changedVersionsReplaced.getOrDefault(Relationship.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
            try (final SearchHitsIterator<Relationship> stream = elasticsearchOperations.searchForStream(
                    componentsReplacedCriteria(changedVersionsReplaced.get(Relationship.class.getSimpleName()), Relationship.Fields.SOURCE_ID).build(), Relationship.class)) {
                stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getSourceId())));
            }
        }
        if (!changedVersionsReplaced.getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
            // Refsets with the internal "conceptId" field are related to a concept in terms of authoring
            NativeQueryBuilder refsetQuery = componentsReplacedCriteria(changedVersionsReplaced.get(ReferenceSetMember.class.getSimpleName()),
                    ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID)
                    .withFilter(bool(b -> b.must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID))));
            try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(refsetQuery.build(), ReferenceSetMember.class)) {
                stream.forEachRemaining(hit -> referenceComponentIdToConceptMap.put(parseLong(hit.getContent().getReferencedComponentId()), parseLong(hit.getContent().getConceptId())));
            }
        }

        // Technique: Search for ended versions
        BoolQuery.Builder updatesDuringRange;
        if (sourceIsParent) {
            updatesDuringRange = versionControlHelper.getUpdatesOnBranchOrAncestorsDuringRangeQuery(path, start, end);
        } else {
            updatesDuringRange = versionControlHelper.getUpdatesOnBranchDuringRangeCriteria(path, start, end);
        }

        // Find new or ended versions of each component type and collect the conceptId they relate to
        logger.debug("Collecting concept changes for change report: branch {} time range {} to {}", path, start, end);

        TimerUtil timerUtil = new TimerUtil("Collecting changes");

        Query updatesQuery = updatesDuringRange.build()._toQuery();
        NativeQuery conceptsWithNewVersionsQuery = new NativeQueryBuilder()
                .withQuery(updatesQuery)
                .withPageable(LARGE_PAGE)
                .withSort(SortOptions.of(s -> s.field(f -> f.field("start"))))
                .withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
                .build();
        try (final SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(conceptsWithNewVersionsQuery, Concept.class)) {
            stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
        }

        logger.debug("Collecting description changes for change report: branch {} time range {} to {}", path, start, end);
        AtomicLong descriptions = new AtomicLong();
        NativeQuery descQuery = newSearchQuery(updatesQuery)
                .withFilter(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
                .withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.CONCEPT_ID}, null))
                .build();
        try (final SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(descQuery, Description.class)) {
            stream.forEachRemaining(hit -> {
                changedConcepts.add(parseLong(hit.getContent().getConceptId()));
                descriptions.incrementAndGet();
            });
        }
        timerUtil.checkpoint("descriptions " + descriptions.get());

        logger.debug("Collecting relationship changes for change report: branch {} time range {} to {}", path, start, end);
        AtomicLong relationships = new AtomicLong();
        NativeQuery relQuery = newSearchQuery(updatesQuery)
                .withSourceFilter(new FetchSourceFilter(new String[]{Relationship.Fields.SOURCE_ID}, null))
                .build();
        try (final SearchHitsIterator<Relationship> stream = elasticsearchOperations.searchForStream(relQuery, Relationship.class)) {
            stream.forEachRemaining(hit -> {
                changedConcepts.add(parseLong(hit.getContent().getSourceId()));
                relationships.incrementAndGet();
            });
        }
        timerUtil.checkpoint("relationships " + relationships.get());

        logger.debug("Collecting refset member changes for change report: branch {} time range {} to {}", path, start, end);
        NativeQuery memberQuery = newSearchQuery(updatesQuery)
                .withFilter(bool(b -> b.must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID))))
                .withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID}, null))
                .build();
        AtomicInteger count = new AtomicInteger();
        try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(memberQuery, ReferenceSetMember.class)) {
            stream.forEachRemaining(hit -> {
                referenceComponentIdToConceptMap.put(parseLong(hit.getContent().getReferencedComponentId()), parseLong(hit.getContent().getConceptId()));
                count.incrementAndGet();
            });
        }
        timerUtil.checkpoint("RefsetMembers " + count.get());

        // Filter out changes for active Synonyms
        // Inactive synonym changes should be included to avoid inactivation indicator / association clashes
        List<Long> synonymAndTextDefIds = new LongArrayList();
        NativeQueryBuilder synonymQuery = new NativeQueryBuilder()
                .withQuery(versionControlHelper.getBranchCriteria(branch).getEntityBranchCriteria(Description.class))
                .withFilter(bool(b -> b
                        .mustNot(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
                        .must(termsQuery(Description.Fields.DESCRIPTION_ID, referenceComponentIdToConceptMap.keySet()))
                        .must(termQuery(Description.Fields.ACTIVE, true))))
                .withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.DESCRIPTION_ID}, null))
                .withPageable(LARGE_PAGE);
        timerUtil.checkpoint("Created synonym query with " + referenceComponentIdToConceptMap.size() + " description ids in filter");
        count.set(0);
        try (final SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(synonymQuery.build(), Description.class)) {
            stream.forEachRemaining(hit -> {
                count.incrementAndGet();
                synonymAndTextDefIds.add(parseLong(hit.getContent().getDescriptionId()));
            });
        }
        timerUtil.checkpoint("Filter out active synonyms " + count.get());

        // Keep preferred terms if any
        NativeQuery languageMemberQuery = newSearchQuery(updatesQuery)
                .withFilter(bool(b -> b
                        .must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID))
                        .must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, synonymAndTextDefIds))
                        .must(termQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED))))
                .withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
                .build();
        count.set(0);
        try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(languageMemberQuery, ReferenceSetMember.class)) {
            stream.forEachRemaining(hit -> {
                count.incrementAndGet();
                preferredDescriptionIds.add(parseLong(hit.getContent().getReferencedComponentId()));
            });
        }
        timerUtil.checkpoint("PT check " + count.get());

        Set<Long> changedComponents = referenceComponentIdToConceptMap.keySet()
                .stream()
                .filter(r -> preferredDescriptionIds.contains(r) || !synonymAndTextDefIds.contains(r))
                .collect(Collectors.toSet());

        for (Long componentId : changedComponents) {
            changedConcepts.add(referenceComponentIdToConceptMap.get(componentId));
        }

        logger.info("Change report complete for branch {} time range {} to {}", path, start, end);

        return changedConcepts;

    }

    private NativeQueryBuilder componentsReplacedCriteria(Set<String> versionsReplaced, String... limitFieldsFetched) {
        NativeQueryBuilder builder = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(termsQuery("_id", versionsReplaced))
                        .mustNot(getNonStatedRelationshipClause())))
                .withPageable(LARGE_PAGE);
        if (limitFieldsFetched.length > 0) {
            builder.withSourceFilter(new FetchSourceFilter(limitFieldsFetched, null));
        }
        return builder;
    }

    private Query getNonStatedRelationshipClause() {
        return termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, List.of(Concepts.INFERRED_RELATIONSHIP, Concepts.ADDITIONAL_RELATIONSHIP));
    }

    private NativeQueryBuilder newSearchQuery(Query query) {
        return new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(query)
                        .mustNot(getNonStatedRelationshipClause())))
                .withPageable(LARGE_PAGE);
    }


    /**
     * Return Concept identifiers that have been deleted on one Branch, with any of the Concepts' Descriptions being modified on
     * the other Branch.
     *
     * @param source The Branch to find either deleted Concepts or modified Descriptions.
     * @param target The Branch to find either deleted Concepts or modified Descriptions.
     * @return Concept identifiers that have been deleted on one Branch, with any of the Concepts' Descriptions being modified on
     * the other Branch.
     */
    public Set<Long> getConceptsWithContradictoryChanges(Branch source, Branch target) {
        Set<String> contradictoryChanges = new HashSet<>();

        Set<String> deletedOnSource = getConceptsDeletedOnBranch(source);
        if (!deletedOnSource.isEmpty()) {
            // Modified on Target (i.e. Task) within Target's own timeline.
            Set<String> modifiedOnTarget = getConceptsWithModifiedDescriptionsOnBranch(target, null, deletedOnSource);
            contradictoryChanges.addAll(modifiedOnTarget);
        }

        Set<String> deletedOnTarget = getConceptsDeletedOnBranch(target);
        if (!deletedOnTarget.isEmpty()) {
            // Modified on Source (i.e. Project) outwith Target's own timeline.
            Set<String> modifiedOnSource = getConceptsWithModifiedDescriptionsOnBranch(source, range().field("start").gt(of(target.getBaseTimestamp())), deletedOnTarget);
            contradictoryChanges.addAll(modifiedOnSource);
        }

        return contradictoryChanges.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    private Set<String> getConceptsDeletedOnBranch(Branch branch) {
        String branchPath = branch.getPath();
        Set<String> conceptsReplacedOnBranch = getConceptsByInternalIds(branchService.findLatest(branchPath).getVersionsReplaced(Concept.class));

        // Find deleted Concepts that only existed on this Branch, i.e. there are no versionsReplaced entries.
        Set<String> conceptsEndedOnBranch = elasticsearchOperations
                .search(new NativeQueryBuilder()
                        .withQuery(
                                bool(b -> b
                                        .must(termQuery(Concept.Fields.PATH, branchPath))
                                        .mustNot(termsQuery(Concept.Fields.CONCEPT_ID, conceptsReplacedOnBranch)))
                        )
                        .withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID, Concept.Fields.END}, null))
                        .withSort(SortOptions.of(s -> s.field(f -> f.field(Concept.Fields.START).order(SortOrder.Desc))))
                        // Group by conceptId. Query will return most recent document for each Concept.
                        .withFieldCollapse(FieldCollapse.of(f -> f.field(Concept.Fields.CONCEPT_ID)))
                        .build(), Concept.class)
                .getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .filter(c -> c.getEnd() != null)
                .map(Concept::getConceptId)
                .collect(Collectors.toSet());

        List<String> branchPathAncestors = SPathUtil.getAncestors(branchPath);
        if (!branchPathAncestors.isEmpty() && !conceptsEndedOnBranch.isEmpty()) {
            try (final SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                    .withQuery(
                            bool(b -> b
                                    .must(termsQuery(Concept.Fields.CONCEPT_ID, conceptsEndedOnBranch))
                                    .must(termsQuery(Concept.Fields.PATH, branchPathAncestors))
                                    .mustNot(existsQuery("end")))
                    )
                    .withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
                    .build(), Concept.class)) {
                // Concept exists on an ancestor, therefore promoted rather than deleted.
                stream.forEachRemaining(hit -> conceptsEndedOnBranch.removeIf(id -> id.equals(hit.getContent().getConceptId())));
            }
        }

        Set<String> conceptsDeleted = new HashSet<>(conceptsEndedOnBranch);

        // Find deleted Concepts that existed on other branches, i.e. there are versionsReplaced entries.
        if (!conceptsReplacedOnBranch.isEmpty()) {
            try (final SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                    .withQuery(
                            bool(b -> b
                                    .must(termsQuery(Concept.Fields.CONCEPT_ID, conceptsReplacedOnBranch))
                                    .must(termQuery(Concept.Fields.PATH, branchPath))
                                    .mustNot(existsQuery(Concept.Fields.END))
                    ))
                    .withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
                    .build(), Concept.class)) {
                // Concept not replaced on current branch, therefore deleted.
                stream.forEachRemaining(hit -> conceptsReplacedOnBranch.removeIf(id -> id.equals(hit.getContent().getConceptId())));
            }
        }

        conceptsDeleted.addAll(conceptsReplacedOnBranch);

        return conceptsDeleted;
    }

    private Set<String> getConceptsByInternalIds(Set<String> internalIds) {
        if (internalIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> conceptIds = new HashSet<>();
        try (final SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withPageable(PageRequest.of(0, internalIds.size()))
                .withQuery(
                        bool(b -> b
                                .must(termsQuery("_id", internalIds)) // ES identifier, not an SCT identifier.
                ))
                .withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
                .build(), Concept.class)) {
            stream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptId()));
        }

        return conceptIds;
    }

    private Set<String> getConceptsWithModifiedDescriptionsOnBranch(Branch branch, RangeQuery.Builder rangeBoundary, Set<String> deletedConceptIds) {
        BoolQuery.Builder queryModifiedDescriptions = bool()
                .must(termQuery(Description.Fields.PATH, branch.getPath()))
                .must(termsQuery(Description.Fields.CONCEPT_ID, deletedConceptIds))
                .mustNot(existsQuery(Description.Fields.END));

        if (rangeBoundary != null) {
            queryModifiedDescriptions.must(rangeBoundary.build()._toQuery());
        }

        Set<String> conceptsWithModifiedDescriptions = new HashSet<>();
        try (final SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withQuery(queryModifiedDescriptions.build()._toQuery())
                .withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.CONCEPT_ID}, null))
                .withSort(SortOptions.of(s -> s.field(f -> f.field(Description.Fields.START).order(SortOrder.Desc))))
                .build(), Description.class)) {
            stream.forEachRemaining(hit -> conceptsWithModifiedDescriptions.add(hit.getContent().getConceptId()));
        }

        return conceptsWithModifiedDescriptions;
    }
}
