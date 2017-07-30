package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.Collection;

public class ExpressionConstraint implements ConceptSelector {

	private ConceptSelector conceptSelector;

	@Override
	public Collection<Long> select(QueryBuilder branchCriteria, QueryService queryService, String path, boolean stated) {
		return conceptSelector.select(branchCriteria, queryService, path, stated);
	}

	public void setConceptSelector(ConceptSelector conceptSelector) {
		this.conceptSelector = conceptSelector;
	}
}
