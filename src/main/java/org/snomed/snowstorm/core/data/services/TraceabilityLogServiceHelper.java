package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;

@Service
public class TraceabilityLogServiceHelper {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	public <T extends SnomedComponent<T>> Iterable<T> loadChangesAndDeletionsWithinOpenCommitOnly(final Commit commit, Class<T> clazz) {
		final BranchCriteria branchCriteriaChangesInCommit = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(branchCriteriaChangesInCommit.getEntityBranchCriteria(clazz))
				.withPageable(LARGE_PAGE)
				.build();
		final List<T> componentResult = new ArrayList<>();
		try (final SearchHitsIterator<T> componentStream = elasticsearchTemplate.searchForStream(searchQuery, clazz)) {
			componentStream.forEachRemaining(componentHit -> componentResult.add(componentHit.getContent()));
		}
		return componentResult;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TraceabilityLogServiceHelper that = (TraceabilityLogServiceHelper) o;
		return Objects.equals(versionControlHelper, that.versionControlHelper) &&
				Objects.equals(elasticsearchTemplate, that.elasticsearchTemplate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(versionControlHelper, elasticsearchTemplate);
	}
}
