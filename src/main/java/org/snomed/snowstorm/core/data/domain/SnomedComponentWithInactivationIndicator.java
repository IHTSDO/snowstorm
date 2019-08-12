package org.snomed.snowstorm.core.data.domain;

import java.util.Set;

public interface SnomedComponentWithInactivationIndicator {

	String getInactivationIndicator();

	String getModuleId();

	String getId();

	ReferenceSetMember getInactivationIndicatorMember();

	Set<ReferenceSetMember> getInactivationIndicatorMembers();

	void addInactivationIndicatorMember(ReferenceSetMember newIndicatorMember);
}
