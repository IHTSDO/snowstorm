package org.snomed.snowstorm.core.data.services.traceability;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class TraceabilityLogServiceHelper {

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	public <T extends SnomedComponent<T>> Iterable<T> loadChangesAndDeletionsWithinOpenCommitOnly(Class<T> clazz, BranchCriteria changesAndDeletionsWithinOpenCommitCriteria,
			String branchPath, Commit commit) {

		final BoolQueryBuilder branchCriteria = changesAndDeletionsWithinOpenCommitCriteria.getEntityBranchCriteria(clazz);

		if (commit.isRebase()) {
			// The rebase branch criteria usually includes component versions brought in from ancestor branches. We will exclude those from traceability.
			branchCriteria.must(termQuery(SnomedComponent.Fields.PATH, branchPath));
		}

		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria)
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(LARGE_PAGE)
				.build();

		final Set<String> componentsWithNewVersion = new HashSet<>();
		final Set<String> componentsWithEndedVersion = new HashSet<>();
		final Map<String, T> componentResult = new HashMap<>();
		try (final SearchHitsIterator<T> componentStream = elasticsearchTemplate.searchForStream(searchQuery, clazz)) {
			componentStream.forEachRemaining(componentHit -> {
				final T component = componentHit.getContent();
				final String componentId = component.getId();
				componentResult.put(componentId, component);
				if (!component.getPath().equals(branchPath)) {
					// A version on ancestor branch was replaced (may be update or delete)
					componentsWithEndedVersion.add(componentId);
				} else {
					if (component.getEnd() == null) {
						// A version on this branch was created (may be update or create)
						componentsWithNewVersion.add(componentId);
					} else {
						// A version on this branch was ended (may be update or delete)
						componentsWithEndedVersion.add(componentId);
					}
				}
			});
		}

		final Collection<T> components = componentResult.values();

		final Map<String, Set<String>> rebaseDuplicatesRemoved = commit.isRebase() ? BranchMetadataHelper.getRebaseDuplicatesRemoved(commit) : Collections.emptyMap();

		// Use new and ended sets to work out if components was created, updated or deleted
		components.forEach(component -> {
			final String componentId = component.getId();
			if (componentsWithNewVersion.contains(componentId)) {
				if (componentsWithEndedVersion.contains(componentId)) {
					component.markChanged();
				} else {
					component.setCreating(true);
				}
			} else {
				if (commit.isRebase() && rebaseDuplicatesRemoved.computeIfAbsent(clazz.getSimpleName(), key -> Collections.emptySet()).contains(componentId)) {
					// Component in child branch is replaced by newer version in parent branch. Log as change, not deletion.
					component.markChanged();
				} else {
					component.markDeleted();
				}
			}
		});
		return components;
	}

}
