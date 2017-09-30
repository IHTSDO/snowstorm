package org.ihtsdo.elasticsnomed.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.core.data.domain.SnomedComponent;
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
	private ComponentTypeRegistry componentTypeRegistry;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService componentService;

	public void createVersion(String effectiveTime, String path) {
		try (Commit commit = branchService.openCommit(path)) {
			QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

			Set<Class<? extends SnomedComponent>> componentTypes = componentTypeRegistry.getComponentTypeRepositoryMap().keySet();
			for (Class<? extends SnomedComponent> componentType : componentTypes) {
				releaseComponentsOfType(componentType, effectiveTime, commit, branchCriteria);
			}
			commit.markSuccessful();
		}
	}

	private <T extends SnomedComponent> void releaseComponentsOfType(Class<T> componentType, String effectiveTime, Commit commit, QueryBuilder branchCriteria) {
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria)
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
