package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.drools.response.InvalidContent;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ContentReportService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.DroolsValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

@RestController
@Tag( name= "Validation", description = "Drools")
@RequestMapping(produces = "application/json")
public class ConceptValidationController {

	@Autowired
	private DroolsValidationService validationService;

	@Autowired
	private ContentReportService contentReportService;

	@PostMapping(value = "/browser/{branch}/validate/concept")
	@Operation(description = "Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcept(@Parameter(description = "The branch path") @PathVariable(value = "branch") @NotNull String branchPath,
			@Parameter(description = "The concept to validate") @RequestBody Concept concept) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcept(branchPath, concept);
	}

	@PostMapping(value = "/browser/{branch}/validate/concepts")
	@Operation(description = "Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcepts(@Parameter(description = "The branch path") @PathVariable(value="branch") @NotNull String branchPath,
			@Parameter(description = "The concepts to validate") @RequestBody Set<Concept> concepts) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcepts(branchPath, concepts);
	}

	@PostMapping(value = "/validation-maintenance/reload-validation-rules")
	@Operation(description = "Reload SNOMED Drools assertions and test resources.")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void reloadDrools() {
		validationService.newRuleExecutorAndResources();
	}

	@GetMapping(value = "/validation-maintenance/semantic-tags")
	@Operation(description = "Retrieve all semantic tags.")
	public Set<String> getSemantictTags() {
		return validationService.getSemanticTags();
	}

	@GetMapping(value = "/{branch}/report/inactive-concepts-without-association")
	@Operation(description = "Find inactive concepts with no historical association grouped by inactivation type.")
	@JsonView(value = View.Component.class)
	public List<ContentReportService.InactivationTypeAndConceptIdList> findInactiveConceptsWithNoHistoricalAssociationByInactivationType(
			@PathVariable(value="branch") @NotNull String branchPath,
			@RequestParam(required = false) String conceptEffectiveTime) {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return contentReportService.findInactiveConceptsWithNoHistoricalAssociationByInactivationType(branchPath, conceptEffectiveTime);
	}

}
