package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.domain.ConceptView;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
public class DummyConceptValidationController {

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/validate/concept", method = RequestMethod.POST, produces = "application/json")
	@JsonView(value = View.Component.class)
	@ApiOperation("Dummy Drools validation.")
	public List<String> validateConcept(@PathVariable String branch, @RequestBody @Valid ConceptView concept) {
		// TODO: Implement Drools validation.
		return new ArrayList<>();
	}

}
