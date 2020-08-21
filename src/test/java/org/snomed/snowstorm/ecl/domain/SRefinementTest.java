package org.snomed.snowstorm.ecl.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class SRefinementTest {

    @Autowired
    private ECLQueryBuilder eclQueryBuilder;

    @Test
    public void getConceptIdsInSimpleExpressionConstraint() {
        String eclExpression = "404684003 |Clinical finding|";

        assertThat(getConceptIdsScenario(eclExpression)).contains("404684003");
    }

    @Test
    public void getConceptIdsInSimpleRefinementExpressionConstraint() {
        String eclExpression = "< 19829001 |Disorder of lung|: 116676008 |Associated morphology| = 79654002 |Edema|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("19829001", "116676008", "79654002");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInComplexRefinementExpressionConstraint() {
        String eclExpression =
                "< 404684003 |Clinical finding|:" +
                        "{" +
                            "363698007 |Finding site| = << 39057004 |Pulmonary valve structure|," +
                            "116676008 |Associated morphology| = << 415582006 |Stenosis|" +
                        "}," +
                        "{" +
                            "363698007 |Finding site| = << 53085002 |Right ventricular structure|," +
                            "116676008 |Associated morphology| = << 56246009 |Hypertrophy|" +
                        "}";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("404684003", "363698007", "39057004", "116676008", "415582006", "53085002", "56246009");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInCardinalityExpressionConstraint() {
        String eclExpression = "< 373873005 |Pharmaceutical / biologic product|: [1..3] 127489000 |Has active ingredient| =< 105590001 |Substance|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("373873005", "127489000", "105590001");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInComplexCardinalityExpressionConstraint() {
        String eclExpression = "< 404684003 |Clinical finding|: [0..0] {[2..*] 363698007 |Finding site| =< 91723000 |Anatomical structure|}";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("404684003", "363698007", "91723000");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInConjunctionExpressionConstraint() {
        String eclExpression = "< 19829001 |Disorder of lung| AND  < 301867009 |Edema of trunk|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("19829001", "301867009");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInDisjunctionExpressionConstraint() {
        String eclExpression = "< 19829001 |Disorder of lung| OR  < 301867009 |Edema of trunk|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("19829001", "301867009");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInExclusionExpressionConstraint() {
        String eclExpression = "<< 19829001 |Disorder of lung| MINUS << 301867009 |Edema of trunk|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("19829001", "301867009");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInNotEqualsExpressionConstraint() {
        String eclExpression = "<  404684003 |Clinical finding|: [0..0] 116676008 |Associated morphology| = << 26036001 |Obstruction|";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("404684003", "116676008", "26036001");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    @Test
    public void getConceptIdsInNestedExpressionConstraint() {
        String eclExpression = "<< (700043003 |Example problem list concepts reference set|)";

        Set<String> actualConceptIds = getConceptIdsScenario(eclExpression);

        Set<String> expectedConceptIds = newHashSet("700043003");
        assertThat(actualConceptIds).containsExactlyInAnyOrderElementsOf(expectedConceptIds);
    }

    private Set<String> getConceptIdsScenario(String eclExpression) {
        ExpressionConstraint expressionConstraint = eclQueryBuilder.createQuery(eclExpression);

        return ((SExpressionConstraint) expressionConstraint).getConceptIds();
    }
}