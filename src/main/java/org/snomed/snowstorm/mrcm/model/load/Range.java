package org.snomed.snowstorm.mrcm.model.load;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import org.snomed.snowstorm.mrcm.model.InclusionType;

import java.util.List;

public class Range {
	private Long conceptId;

	private InclusionType inclusionType;

	@JacksonXmlElementWrapper(useWrapping=false)
	private List<Children> children;

	public Long getConceptId() {
		return conceptId;
	}

	public void setConceptId(Long conceptId) {
		this.conceptId = conceptId;
	}

	public InclusionType getInclusionType() {
		return inclusionType;
	}

	public void setInclusionType(InclusionType inclusionType) {
		this.inclusionType = inclusionType;
	}

	public List<Children> getChildren() {
		return children;
	}

	public void setChildren(List<Children> children) {
		this.children = children;
	}
}
