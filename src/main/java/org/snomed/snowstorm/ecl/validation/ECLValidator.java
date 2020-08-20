package org.snomed.snowstorm.ecl.validation;

import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Service
public class ECLValidator {

    private final ECLQueryBuilder eclQueryBuilder;

    private final QueryService queryService;

    public ECLValidator(ECLQueryBuilder eclQueryBuilder, QueryService queryService) {
        this.eclQueryBuilder = eclQueryBuilder;
        this.queryService = queryService;
    }

    public void validateEcl(String ecl, String branch) {
        ExpressionConstraint expressionConstraint = eclQueryBuilder.createQuery(ecl);
        validateConceptIds(expressionConstraint, branch);
    }

    private void validateConceptIds(ExpressionConstraint expressionConstraint, String branch) {
        Set<String> conceptIds = ((SExpressionConstraint) expressionConstraint).getConceptIds();
        QueryService.ConceptQueryBuilder conceptQueryBuilder = queryService.createQueryBuilder(false).activeFilter(false).conceptIds(conceptIds);
        Page<ConceptMini> conceptMiniPage = queryService.search(conceptQueryBuilder, branch, PageRequest.of(0, 1000));
        List<ConceptMini> inactiveConcepts = conceptMiniPage.getContent().stream().filter(conceptMini -> !conceptMini.getActive()).collect(toList());
        if (!CollectionUtils.isEmpty(inactiveConcepts)) {
            String inactiveConceptIds = inactiveConcepts.stream().map(ConceptMini::getConceptId).collect(joining());
            throw new IllegalArgumentException("Concepts in the ECL request do not exist or are inactive on branch " + branch + ": " + inactiveConceptIds + ".");
        }
    }
}