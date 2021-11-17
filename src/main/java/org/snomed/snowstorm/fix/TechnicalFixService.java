package org.snomed.snowstorm.fix;

import org.snomed.snowstorm.fix.service.RedundantVersionsReplacedFixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TechnicalFixService {

	@Autowired
	private RedundantVersionsReplacedFixService redundantVersionsReplacedFixService;

	public String runTechnicalFix(TechnicalFixType technicalFixType, String branchPath) {
		if (technicalFixType == TechnicalFixType.REDUNDANT_VERSIONS_REPLACED_MEMBERS) {
			return redundantVersionsReplacedFixService.reduceMembersReplaced(branchPath);
		} else {
			throw new IllegalArgumentException("Unrecognised technical fix type.");
		}
	}
}
