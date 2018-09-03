package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

@Service
public class ReleaseService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService componentService;

	public void createVersion(Integer effectiveTime, String path) {
		try (Commit commit = branchService.openCommit(path)) {
			BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);

			Set<Class<? extends SnomedComponent>> componentTypes = domainEntityConfiguration.getComponentTypeRepositoryMap().keySet();
			for (Class<? extends SnomedComponent> componentType : componentTypes) {
				releaseComponentsOfType(componentType, effectiveTime, commit, branchCriteria);
			}
			commit.markSuccessful();
		}
	}

	private <T extends SnomedComponent> void releaseComponentsOfType(Class<T> componentType, Integer effectiveTime, Commit commit, BranchCriteria branchCriteria) {
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(componentType))
						.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME))
				)
				.withPageable(LARGE_PAGE)
				.build();

		List<T> componentsToSave = new ArrayList<>();
		try (CloseableIterator<T> stream = elasticsearchOperations.stream(searchQuery, componentType)) {
			stream.forEachRemaining(c -> {
				c.release(effectiveTime);
				c.markChanged();
				componentsToSave.add(c);
			});
		}

		componentService.doSaveBatchComponents(componentsToSave, componentType, commit);
	}

}
