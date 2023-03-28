package org.snomed.snowstorm.core.data.services.pojo;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

public class IdentifierSearchRequest {

	private Boolean active;
	private Boolean isNullEffectiveTime;
	private String module;
	private String alternateIdentifier;
	private String identifierSchemeId;
	private Collection<? extends Serializable> referencedComponentIds;

	public IdentifierSearchRequest() {
	}

	/**
	 * @param active  Filter by the active field.
	 */
	public IdentifierSearchRequest active(Boolean active) {
		this.active = active;
		return this;
	}

	public Boolean getActive() {
		return active;
	}


	public IdentifierSearchRequest module(String module) {
		this.module = module;
		return this;
	}

	public String getModule() {
		return module;
	}

	public IdentifierSearchRequest setAlternateIdentifier(String alternateIdentifier) {
		this.alternateIdentifier = alternateIdentifier;
		return this;
	}

	public String getAlternateIdentifier() {
		return alternateIdentifier;
	}

	public IdentifierSearchRequest setIdentifierSchemeId(String identifierSchemeId) {
		this.identifierSchemeId = identifierSchemeId;
		return this;
	}

	public String getIdentifierSchemeId() {
		return identifierSchemeId;
	}

	/**
	 * @param referencedComponentIds Filter by the referencedComponentId field.
	 */
	public IdentifierSearchRequest referencedComponentIds(Collection<? extends Serializable> referencedComponentIds) {
		this.referencedComponentIds = referencedComponentIds;
		return this;
	}

	public IdentifierSearchRequest referencedComponentId(String referencedComponentId) {
		return referencedComponentIds(Collections.singleton(referencedComponentId));
	}

	public Collection<? extends Serializable> getReferencedComponentIds() {
		return referencedComponentIds;
	}



	public IdentifierSearchRequest isNullEffectiveTime(Boolean isNullEffectiveTime) {
		this.isNullEffectiveTime = isNullEffectiveTime;
		return this;
	}
	
	public Boolean isNullEffectiveTime() {
		return this.isNullEffectiveTime;
	}
}
