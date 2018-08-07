package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.View;

public class ConceptMiniNestedFsn {

	private ConceptMini concept;

	public ConceptMiniNestedFsn(ConceptMini concept) {
		this.concept = concept;
	}

	@JsonView(value = View.Component.class)
	public String getId() {
		return concept.getConceptId();
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return concept.getConceptId();
	}

	@JsonView(value = View.Component.class)
	public FSNHolder getFsn() {
		String term = concept.getFsn();
		if (term != null) {
			return new FSNHolder(term);
		}
		return null;
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return concept.getDefinitionStatus();
	}

	@JsonView(value = View.Component.class)
	public Boolean getIsLeafInferred() {
		return concept.getIsLeafInferred();
	}

	@JsonView(value = View.Component.class)
	public Boolean getIsLeafStated() {
		return concept.getIsLeafStated();
	}

	@JsonView(value = View.Component.class)
	public String getModuleId() {
		return concept.getModuleId();
	}

	@JsonView(value = View.Component.class)
	public Boolean getActive() {
		return concept.getActive();
	}

	public static final class FSNHolder {
		private String term;

		FSNHolder(String term) {
			this.term = term;
		}

		@JsonView(value = View.Component.class)
		public String getTerm() {
			return term;
		}
	}

}
