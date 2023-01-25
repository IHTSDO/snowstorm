package org.snomed.snowstorm.core.data.domain;

import java.util.Collection;

public interface SnomedComponentWithInactivationIndicator {

	String getInactivationIndicator();

	String getInactivationIndicatorName();

	String getModuleId();

	String getId();

	ReferenceSetMember getInactivationIndicatorMember();

	Collection<ReferenceSetMember> getInactivationIndicatorMembers();

	void addInactivationIndicatorMember(ReferenceSetMember newIndicatorMember);
}
