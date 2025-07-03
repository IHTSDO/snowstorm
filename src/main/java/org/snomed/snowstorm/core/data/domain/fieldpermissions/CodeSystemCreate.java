package org.snomed.snowstorm.core.data.domain.fieldpermissions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.snomed.snowstorm.core.data.domain.CodeSystem;

@JsonDeserialize(as = CodeSystem.class)
public interface CodeSystemCreate {

	String getShortName();
	String getUriModuleId();
	String getName();
	String getOwner();
	String getCountryCode();
	String getMaintainerType();
	String getDefaultLanguageCode();
	String[] getDefaultLanguageReferenceSets();
	String getBranchPath();
	Integer getDependantVersionEffectiveTime();
}
