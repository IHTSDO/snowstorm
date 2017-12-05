package org.snomed.snowstorm.mrcm.model.load;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName="ConceptModel")
public class ConceptModel {

	@JacksonXmlElementWrapper(useWrapping=false)
	private List<Constraints> constraints;

	public List<Constraints> getConstraints() {
		return constraints;
	}

	public void setConstraints(List<Constraints> constraints) {
		this.constraints = constraints;
	}
}
