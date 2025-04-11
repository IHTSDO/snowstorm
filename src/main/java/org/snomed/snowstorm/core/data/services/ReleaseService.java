package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.existsQuery;

@Service
public class ReleaseService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;
	
	@Autowired
	private ModuleDependencyService mdService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService componentService;

	public void createVersion(Integer effectiveTime, String path) {
		mdService.setSourceAndTargetEffectiveTimes(path, effectiveTime);

		try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Versioning components using effectiveTime " + effectiveTime))) {

			// Disable traceability when versioning to prevent logging every component in the release
			BranchMetadataHelper.markCommitAsCreatingCodeSystemVersion(commit);
			
			BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);

			Set<Class<? extends SnomedComponent<?>>> componentTypes = domainEntityConfiguration.getComponentTypeRepositoryMap().keySet();
			for (Class<? extends SnomedComponent> componentType : componentTypes) {
				releaseComponentsOfType(componentType, effectiveTime, commit, branchCriteria);
			}

			// Assume versioned content is classified
			BranchClassificationStatusService.setClassificationStatus(commit.getBranch(), true);

			commit.markSuccessful();
		}
	}

	private <T extends SnomedComponent<?>> void releaseComponentsOfType(Class<T> componentType, Integer effectiveTime, Commit commit, BranchCriteria branchCriteria) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(branchCriteria.getEntityBranchCriteria(componentType))
						.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME))
				))
				.withPageable(LARGE_PAGE)
				.build();

		List<T> componentsToSave = new ArrayList<>();
		try (SearchHitsIterator<T> stream = elasticsearchOperations.searchForStream(searchQuery, componentType)) {
			stream.forEachRemaining(hit -> {
				hit.getContent().release(effectiveTime);
				hit.getContent().markChanged();
				componentsToSave.add(hit.getContent());
			});
		}

		componentService.doSaveBatchComponents(componentsToSave, componentType, commit);
	}

}
