package org.snomed.snowstorm.core.data.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface SnomedComponentWithAssociations {

	Map<String,Set<String>> getAssociationTargets();

	Collection<ReferenceSetMember> getAssociationTargetMembers();

	String getModuleId();

	String getId();

	void addAssociationTargetMember(ReferenceSetMember newTargetMember);
}
