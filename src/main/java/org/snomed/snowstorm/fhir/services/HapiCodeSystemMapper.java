package org.snomed.snowstorm.fhir.services;

import java.text.SimpleDateFormat;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemHierarchyMeaning;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.CodeSystemConfigurationService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;

public class HapiCodeSystemMapper implements FHIRConstants {
	
	@Autowired
	private CodeSystemConfigurationService codeSystemConfigurationService;
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
	
	public CodeSystem mapToFHIR(CodeSystemVersion cv) {
		CodeSystem c = getStandardCodeSystem();
		String moduleId = codeSystemConfigurationService.getDefaultModuleId(cv.getShortName());
		String uri = SNOMED_URI + "/" + moduleId + VERSION + cv.getEffectiveDate();
		String id = "sct_" + moduleId + "_" + cv.getEffectiveDate();
		c.setId(id);
		c.setUrl(uri);
		c.setVersion(cv.getEffectiveDate().toString());
		try {
			c.setDate(sdf.parse(cv.getEffectiveDate().toString()));
		} catch (Exception e) {}
		return c;
	}
	
	private CodeSystem getStandardCodeSystem() {
		CodeSystem cs = new CodeSystem();
		cs.setPublisher(SNOMED_INTERNATIONAL);
		cs.setStatus(PublicationStatus.ACTIVE);
		cs.setHierarchyMeaning(CodeSystemHierarchyMeaning.ISA);
		cs.setCompositional(true);
		cs.setContent(CodeSystemContentMode.COMPLETE);
		return cs;
	}


}
