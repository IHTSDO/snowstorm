package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.services.AuthoringMirrorService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.DescriptionService;
import org.ihtsdo.elasticsnomed.rest.pojo.DescriptionSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AuthoringMirrorController {

	@Autowired
	private AuthoringMirrorService authoringMirrorService;

	@RequestMapping(value = "/authoring-mirror/activities", method = RequestMethod.POST)
	public void mirrorAuthoring(@RequestParam("traceability-file") MultipartFile traceabilityFile) throws IOException {
		authoringMirrorService.receiveActivityFile(traceabilityFile);
	}

}
