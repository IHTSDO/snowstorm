package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.SearchType;
import org.snomed.langauges.ecl.domain.filter.TypedSearchTerm;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ECLToStringUtil {

	public static void toString(StringBuffer buffer, ConceptReference conceptReference) {
		buffer.append(conceptReference.getConceptId());
		if (conceptReference.getTerm() != null) {
			buffer.append(" |").append(conceptReference.getTerm()).append("|");
		}
	}

	public static void toString(StringBuffer buffer, List<ConceptReference> conceptReferences) {
		if (conceptReferences != null) {
			buffer.append(" ");
			if (conceptReferences.size() > 1) {
				buffer.append("(");
			}
			int i = 0;
			for (ConceptReference conceptReference : conceptReferences) {
				if (i++ > 0) {
					buffer.append(" ");
				}
				toString(buffer, conceptReference);
			}
			if (conceptReferences.size() > 1) {
				buffer.append(")");
			}
		}
	}

	public static void toStringTypedSearchTerms(StringBuffer buffer, List<TypedSearchTerm> typedSearchTermSet) {
		if (typedSearchTermSet != null) {
			buffer.append(" ");
			if (typedSearchTermSet.size() > 1) {
				buffer.append("(");
			}
			boolean anyWildcards = typedSearchTermSet.stream().anyMatch(term -> term.getType() == SearchType.WILDCARD);
			int i = 0;
			for (TypedSearchTerm typedSearchTerm : typedSearchTermSet) {
				if (i++ > 0) {
					buffer.append(" ");
				}
				if (anyWildcards) {
					if (typedSearchTerm.getType() == SearchType.MATCH) {
						buffer.append("match:");
					} else {
						buffer.append("wild:");
					}
				}
				buffer.append("\"").append(typedSearchTerm.getTerm()).append("\"");
			}
			if (typedSearchTermSet.size() > 1) {
				buffer.append(")");
			}
		}
	}

	public static void toString(StringBuffer buffer, SubExpressionConstraint subExpressionConstraint) {
		if (subExpressionConstraint != null) {
			buffer.append(" ");
			((SSubExpressionConstraint)subExpressionConstraint).toString(buffer);
		}
	}

	public static void toString(StringBuffer buffer, Collection<Integer> effectiveTimeValues) {
		if (effectiveTimeValues != null) {
			buffer.append(" ");
			if (effectiveTimeValues.size() > 1) {
				buffer.append("(");
			}
			int i = 0;
			for (Integer value : effectiveTimeValues) {
				if (i++ > 0) {
					buffer.append(" ");
				}
				buffer.append("\"").append(value).append("\"");
			}
			if (effectiveTimeValues.size() > 1) {
				buffer.append(")");
			}
		}
	}
}
