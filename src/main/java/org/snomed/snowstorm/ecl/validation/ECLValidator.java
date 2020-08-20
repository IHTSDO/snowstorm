package org.snomed.snowstorm.ecl.validation;

import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.QueryService.ConceptQueryBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.subtract;
import static org.springframework.util.CollectionUtils.isEmpty;

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
        ConceptQueryBuilder conceptQueryBuilder = queryService.createQueryBuilder(false).conceptIds(conceptIds);
        Page<ConceptMini> conceptMiniPage = queryService.search(conceptQueryBuilder, branch, PageRequest.of(0, 1000));
        List<String> inactiveConceptIds = conceptMiniPage.getContent().stream().filter(conceptMini -> !conceptMini.getActive()).map(ConceptMini::getConceptId).collect(toList());
        List<String> nonexistentConceptIds = (List<String>) subtract(conceptIds, conceptMiniPage.getContent().stream().map(ConceptMini::getConceptId).collect(toList()));
        throwsIllegalArgumentException(inactiveConceptIds, nonexistentConceptIds, branch);
    }

    private void throwsIllegalArgumentException(List<String> inactiveConceptIds, List<String> nonexistentConceptIds, String branch) {
        if (!isEmpty(inactiveConceptIds) || !isEmpty(nonexistentConceptIds)) {
            List<String> invalidConceptIds = newArrayList();
            invalidConceptIds.addAll(inactiveConceptIds);
            invalidConceptIds.addAll(nonexistentConceptIds);
            throw new IllegalArgumentException("Concepts in the ECL request do not exist or are inactive on branch " + branch + ": " +
                    String.join(", ", invalidConceptIds) + ".");
        }
    }
}