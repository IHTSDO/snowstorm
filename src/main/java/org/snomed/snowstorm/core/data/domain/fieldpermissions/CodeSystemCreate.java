package org.snomed.snowstorm.core.data.domain.fieldpermissions;

import com.fasterxml.jackson.annotation.JsonDeserializeAs;
import org.snomed.snowstorm.core.data.domain.CodeSystem;

@JsonDeserializeAs(CodeSystem.class)
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
