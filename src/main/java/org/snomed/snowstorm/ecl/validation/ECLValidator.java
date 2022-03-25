package org.snomed.snowstorm.ecl.validation;

import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.QueryService.ConceptQueryBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.String.join;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.CollectionUtils.subtract;

@Service
public class ECLValidator {

	private final ECLQueryBuilder eclQueryBuilder;

	private final QueryService queryService;

	public ECLValidator(ECLQueryBuilder eclQueryBuilder, QueryService queryService) {
		this.eclQueryBuilder = eclQueryBuilder;
		this.queryService = queryService;
	}

	public void validate(String ecl, String branch) {
		ExpressionConstraint expressionConstraint = eclQueryBuilder.createQuery(ecl);
		validateConceptIds(expressionConstraint, branch);
	}

	private void validateConceptIds(ExpressionConstraint expressionConstraint, String branch) {
		Set<String> conceptIds = ((SExpressionConstraint) expressionConstraint).getConceptIds();
		ConceptQueryBuilder conceptQueryBuilder = queryService.createQueryBuilder(false).activeFilter(true).conceptIds(conceptIds);
		Page<Long> retrievedConceptIdsPage = queryService.searchForIds(conceptQueryBuilder, branch, LARGE_PAGE);
		Set<String> retrievedConceptIds = retrievedConceptIdsPage.getContent().stream().map(conceptId -> Long.toString(conceptId)).collect(toSet());
		List<String> invalidConceptIds = (List<String>) subtract(conceptIds, retrievedConceptIds);
		if (isNotEmpty(invalidConceptIds)) {
			throw new IllegalArgumentException("Concepts in the ECL request do not exist or are inactive on branch " + branch + ": " +
					join(", ", invalidConceptIds) + ".");
		}
	}
}
