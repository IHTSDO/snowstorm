package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.Acceptability;
import org.snomed.langauges.ecl.domain.filter.DialectAcceptability;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

public class SDialectAcceptability extends DialectAcceptability {

	@SuppressWarnings("unused")
	private SDialectAcceptability() {
		// For JSON
	}

	public SDialectAcceptability(ConceptReference dialectId) {
		super(dialectId);
	}

	public SDialectAcceptability(SubExpressionConstraint subExpressionConstraint) {
		super(subExpressionConstraint);
	}

	public SDialectAcceptability(String dialectAlias) {
		super(dialectAlias);
	}

	public void toString(StringBuffer buffer) {
		if (getDialectId() != null) {
			ECLToStringUtil.toString(buffer, getDialectId());
		} else if (getSubExpressionConstraint() != null) {
			((SSubExpressionConstraint) getSubExpressionConstraint()).toString(buffer);
		} else {
			buffer.append(getDialectAlias());
		}
		if (getAcceptabilityIdSet() != null) {
			buffer.append(" (");
			int a = 0;
			for (ConceptReference conceptReference : getAcceptabilityIdSet()) {
				if (a++ > 0) {
					buffer.append(" ");
				}
				ECLToStringUtil.toString(buffer, conceptReference);
			}
			buffer.append(")");
		} else if (getAcceptabilityTokenSet() != null) {
			buffer.append(" (");
			int a = 0;
			for (Acceptability acceptability : getAcceptabilityTokenSet()) {
				if (a++ > 0) {
					buffer.append(" ");
				}
				buffer.append(acceptability.getToken());
			}
			buffer.append(")");
		}
	}
}
