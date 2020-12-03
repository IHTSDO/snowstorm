package org.snomed.snowstorm.core.data.services.persistedcomponent;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;

@Component
public abstract class AbstractPersistedComponentLoader {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	/**
	 * Loads the components from the {@link BranchCriteria} specified.
	 *
	 * @param commit Used to determine the {@link BranchCriteria}.
	 * @param persistedComponentLoader Used to set the components into
	 *                                 the persisted component.
	 * @param clazz Class of the component being analysed.
	 * @param persistedComponent The persisted component which contains
	 *                           the results retrieved from the load operation.
	 * @param <T> Type of the {@link SnomedComponent}.
	 */
	public final <T extends SnomedComponent<T>> void load(final Commit commit, final PersistedComponentLoader<T> persistedComponentLoader,
			final Class<T> clazz, final Builder persistedComponent) {
		final BranchCriteria branchCriteriaChangesInCommit = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(branchCriteriaChangesInCommit.getEntityBranchCriteria(clazz))
				.withPageable(LARGE_PAGE)
				.build();
		final List<T> componentResult = new ArrayList<>();
		try (final SearchHitsIterator<T> componentStream = elasticsearchTemplate.searchForStream(searchQuery, clazz)) {
			componentStream.forEachRemaining(componentHit -> componentResult.add(componentHit.getContent()));
		}
		persistedComponentLoader.setPersistedComponents(componentResult, persistedComponent);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AbstractPersistedComponentLoader that = (AbstractPersistedComponentLoader) o;
		return Objects.equals(versionControlHelper, that.versionControlHelper) &&
				Objects.equals(elasticsearchTemplate, that.elasticsearchTemplate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(versionControlHelper, elasticsearchTemplate);
	}
}
