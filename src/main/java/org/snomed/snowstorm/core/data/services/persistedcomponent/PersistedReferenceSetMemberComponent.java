package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;

public class PersistedReferenceSetMemberComponent implements PersistedComponentLoader<ReferenceSetMember> {

	@Override
	public void setPersistedComponents(final Iterable<ReferenceSetMember> components, final Builder builder) {
		builder.withPersistedReferenceSetMembers(components);
	}

	@Override
	public Class<ReferenceSetMember> getComponent() {
		return ReferenceSetMember.class;
	}
}
