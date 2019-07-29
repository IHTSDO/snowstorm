package org.snomed.snowstorm.core.data.domain;

import com.google.common.collect.Sets;

import java.util.Set;

public enum Form {

	STATED("stated", true, Sets.newHashSet(Concepts.STATED_RELATIONSHIP, Concepts.ADDITIONAL_RELATIONSHIP)),
	INFERRED("inferred", false, Sets.newHashSet(Concepts.INFERRED_RELATIONSHIP, Concepts.ADDITIONAL_RELATIONSHIP));

	private String name;
	private boolean stated;
	private Set<String> characteristicTypeIds;

	Form(String name, boolean stated, Set<String> characteristicTypeIds) {
		this.name = name;
		this.stated = stated;
		this.characteristicTypeIds = characteristicTypeIds;
	}

	public String getName() {
		return name;
	}

	public boolean isStated() {
		return stated;
	}

	public Set<String> getCharacteristicTypeIds() {
		return characteristicTypeIds;
	}
}
