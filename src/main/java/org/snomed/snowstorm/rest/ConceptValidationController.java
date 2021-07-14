package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.ihtsdo.drools.response.InvalidContent;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ContentReportService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.validation.DroolsValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "Validation", description = "Drools")
@RequestMapping(produces = "application/json")
public class ConceptValidationController {

	@Autowired
	private DroolsValidationService validationService;

	@Autowired
	private ContentReportService contentReportService;

	@Autowired
	private BranchService branchService;

	@RequestMapping(value = "/browser/{branch}/validate/concept", method = RequestMethod.POST)
	@ApiOperation(value = "Validation using the Snomed-Drools project.",
			notes = "The afterClassification flag runs additional validation when using the snomed-term-validation service. If this flag is not set explicitly the branch " +
					"classification status will be used.")
	public List<InvalidContent> validateConcept(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull String branchPath,
			@RequestParam(required = false) Boolean afterClassification,
			@ApiParam(value="The concept to validate") @RequestBody Concept concept) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcepts(branchPath, Collections.singleton(concept), getAfterClassification(branchPath, afterClassification));
	}

	@RequestMapping(value = "/browser/{branch}/validate/concepts", method = RequestMethod.POST)
	@ApiOperation(value = "Validation using the Snomed-Drools project.",
			notes = "The afterClassification flag runs additional validation when using the snomed-term-validation service. If this flag is not set explicitly the branch " +
					"classification status will be used.")
	public List<InvalidContent> validateConcepts(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull String branchPath,
			@RequestParam(required = false) Boolean afterClassification,
			@ApiParam(value="The concepts to validate") @RequestBody Set<Concept> concepts) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcepts(branchPath, concepts, getAfterClassification(branchPath, afterClassification));
	}

	private boolean getAfterClassification(String branchPath, Boolean afterClassification) {
		return afterClassification != null ? afterClassification :
				Boolean.TRUE.equals(BranchClassificationStatusService.getClassificationStatus(branchService.findBranchOrThrow(branchPath)));
	}

	@RequestMapping(value = "/validation-maintenance/reload-validation-rules", method = RequestMethod.POST)
	@ApiOperation("Reload SNOMED Drools assertions and test resources.")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void reloadDrools() {
		validationService.newRuleExecutorAndResources();
	}

	@RequestMapping(value = "/{branch}/report/inactive-concepts-without-association", method = RequestMethod.GET)
	@ApiOperation("Find inactive concepts with no historical association grouped by inactivation type.")
	@JsonView(value = View.Component.class)
	public List<ContentReportService.InactivationTypeAndConceptIdList> findInactiveConceptsWithNoHistoricalAssociationByInactivationType(
			@PathVariable(value="branch") @NotNull String branchPath,
			@RequestParam(required = false) String conceptEffectiveTime) {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return contentReportService.findInactiveConceptsWithNoHistoricalAssociationByInactivationType(branchPath, conceptEffectiveTime);
	}

}
