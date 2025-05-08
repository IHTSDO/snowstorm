package org.snomed.snowstorm.core.data.repositories;

import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportStatusRepository extends ElasticsearchRepository<SyndicationImport, String> {}