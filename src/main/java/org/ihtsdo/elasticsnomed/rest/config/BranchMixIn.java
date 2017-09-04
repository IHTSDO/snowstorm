package org.ihtsdo.elasticsnomed.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.Entity;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public abstract class BranchMixIn {

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getBase();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getHead();

	@JsonIgnore
	abstract Set<String> getVersionsReplaced();

	@JsonIgnore
	abstract Date getStart();

	@JsonIgnore
	abstract Map<String, String> getMetadataInternal();

}
