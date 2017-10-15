package org.ihtsdo.elasticsnomed.ecl.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

public class ESBooleanQuery {

	@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
	private List<ESQuery> must;

	@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
	private List<ESQuery> should;

	public ESBooleanQuery() {
	}

	public List<ESQuery> getMust() {
		return must;
	}

	public void setMust(List<ESQuery> must) {
		this.must = must;
	}

	public List<ESQuery> getShould() {
		return should;
	}

	public void setShould(List<ESQuery> should) {
		this.should = should;
	}
}
