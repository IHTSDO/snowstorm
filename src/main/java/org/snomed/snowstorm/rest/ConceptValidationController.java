package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
@Api(tags = "Validation", description = "Drools")
@RequestMapping(produces = "application/json")
public class ConceptValidationController {

	@Autowired
	private DroolsValidationService validationService;

	@Autowired
	private ContentReportService contentReportService;

	@RequestMapping(value = "/browser/{branch}/validate/concept", method = RequestMethod.POST)
	@ApiOperation("Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcept(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull String branchPath,
			@ApiParam(value="The concept to validate") @RequestBody Concept concept) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcept(branchPath, concept);
	}

	@RequestMapping(value = "/browser/{branch}/validate/concepts", method = RequestMethod.POST)
	@ApiOperation("Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcepts(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull String branchPath,
			@ApiParam(value="The concepts to validate") @RequestBody Set<Concept> concepts) throws ServiceException {

		branchPath = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcepts(branchPath, concepts);
	}

	@RequestMapping(value = "/validation-maintenance/reload-validation-rules", method = RequestMethod.POST)
	@ApiOperation("Reload SNOMED Drools assertions and test resources.")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void reloadDrools() throws ServiceException {
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
