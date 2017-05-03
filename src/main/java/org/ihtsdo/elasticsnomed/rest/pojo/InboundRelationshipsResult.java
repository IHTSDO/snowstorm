package org.ihtsdo.elasticsnomed.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.rest.View;

import java.util.List;

public class InboundRelationshipsResult {

	private List<Relationship> inboundRelationships;

	public InboundRelationshipsResult(List<Relationship> inboundRelationships) {
		this.inboundRelationships = inboundRelationships;
	}

	@JsonView(value = View.Component.class)
	public List<Relationship> getInboundRelationships() {
		return inboundRelationships;
	}

	@JsonView(value = View.Component.class)
	public int getTotal() {
		return inboundRelationships.size();
	}
}
