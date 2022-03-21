package org.snomed.snowstorm.ecl.domain;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.ecl.ECLContentService;

import java.util.function.Function;

public interface RefinementBuilder {

	BoolQueryBuilder getQuery();

	Function<QueryConcept, Boolean> getInclusionFilter();

	void setInclusionFilter(Function<QueryConcept, Boolean> inclusionFilter);

	String getPath();

	BranchCriteria getBranchCriteria();

	boolean isStated();

	ECLContentService getEclContentService();

	void inclusionFilterRequired();

	boolean isInclusionFilterRequired();
}
