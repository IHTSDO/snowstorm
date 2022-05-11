package org.snomed.snowstorm.fix;

import io.kaicode.elasticvc.domain.Branch;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fix.service.RedundantVersionsReplacedFixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TechnicalFixService {

	@Autowired
	private RedundantVersionsReplacedFixService redundantVersionsReplacedFixService;

	@Autowired
	private CodeSystemService codeSystemService;

	public String runTechnicalFix(TechnicalFixType technicalFixType, String branchPath) {
		if (technicalFixType == TechnicalFixType.REDUNDANT_VERSIONS_REPLACED_MEMBERS) {
			return redundantVersionsReplacedFixService.reduceMembersReplaced(branchPath);
		} else if (technicalFixType == TechnicalFixType.CREATE_EMPTY_2000_VERSION) {
			if (!Branch.MAIN.equals(branchPath)) {
				throw new IllegalArgumentException("Creating an empty code system version can only run on the root branch MAIN.");
			}
			return codeSystemService.createEmpty2000Version();
		} else {
			throw new IllegalArgumentException("Unrecognised technical fix type.");
		}
	}
}
