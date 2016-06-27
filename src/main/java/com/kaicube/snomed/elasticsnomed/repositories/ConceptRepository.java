package com.kaicube.snomed.elasticsnomed.repositories;

import com.kaicube.snomed.elasticsnomed.domain.Concept;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;

public interface ConceptRepository extends ElasticsearchCrudRepository<Concept, String> {

	Page<Concept> findAll(Pageable pageable);

//	List<Concept> findByConceptIdAndBranch(Long id, String branch);

//	Page<Concept> findByConceptIdAndBranchAndCommitLessThanEqual(Long id, String branch, Long head, Pageable page);

	Iterable<Concept> findByPath(String path);

	Iterable<Concept> findByPath(String path, PageRequest pageRequest);

//	@Query("{\"bool\" : {\"must\" : {\"field\" : {\"sctid\" : \"?0\"}}}}")
//	Concept findByBranch(Branch branch);
//	Concept findBySctidAndBranch(Long sctid, Branch branch);
}
