package org.snomed.snowstorm.core.data.services.identifier.cis;

import com.google.common.base.Joiner;

import java.util.Collection;

final class CISBulkGetRequest implements CISBulkRequest {

	private Collection<Long> sctids;

	public CISBulkGetRequest(Collection<Long> sctids) {
		this.sctids = sctids;
	}

	public String getSctids() {
		return Joiner.on(",").join(sctids);
	}

	@Override
	public int size() {
		return sctids.size();
	}
}
