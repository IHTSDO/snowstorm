package org.ihtsdo.elasticsnomed.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.index.query.QueryBuilders;
import org.ihtsdo.elasticsnomed.core.data.domain.Classification;
import org.ihtsdo.elasticsnomed.core.data.repositories.ClassificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.GetQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import sun.reflect.generics.repository.ClassRepository;

import java.util.Date;
import java.util.UUID;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class ClassificationService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ClassificationRepository classificationRepository;

	private static final PageRequest PAGE_FIRST_1K = new PageRequest(0, 1000);

	public Page<Classification> findClassifications(String path) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termQuery(Classification.Fields.PATH, path))
				.withPageable(PAGE_FIRST_1K);
		return elasticsearchOperations.queryForPage(queryBuilder.build(), Classification.class);
	}

	public Classification findClassification(String path, String classificationId) {
		GetQuery getQuery = new GetQuery();
		getQuery.setId(classificationId);
		Classification classification = elasticsearchOperations.queryForObject(getQuery, Classification.class);
		if (classification == null || !path.equals(classification.getPath())) {
			throw new NotFoundException("Classification not found on branch.");
		}
		return path.equals(classification.getPath()) ? classification : null;
	}

	public Classification createClassification(String path, String reasonerId) {
		Branch branch = branchService.findBranchOrThrow(path);

		Classification classification = new Classification();
		classification.setId(UUID.randomUUID().toString());
		classification.setPath(path);
		classification.setStatus(Classification.Status.SCHEDULED);
		classification.setReasonerId(reasonerId);
		// TODO: set user ID when we have IMS integration
		classification.setCreationDate(new Date());
		classification.setLastCommitDate(branch.getHead());
		classificationRepository.save(classification);

		return classification;
	}

	public void saveClassificationResults(String path, String classificationId) {
		// TODO: implement this
	}
}
