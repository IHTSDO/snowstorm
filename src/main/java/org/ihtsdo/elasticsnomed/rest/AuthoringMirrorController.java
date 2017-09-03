package org.ihtsdo.elasticsnomed.rest;

import org.ihtsdo.elasticsnomed.core.data.services.AuthoringMirrorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(produces = "application/json")
public class AuthoringMirrorController {

	@Autowired
	private AuthoringMirrorService authoringMirrorService;

	@RequestMapping(value = "/authoring-mirror/activities", method = RequestMethod.POST)
	public void mirrorAuthoring(@RequestParam("traceability-file") MultipartFile traceabilityFile) throws IOException {
		authoringMirrorService.receiveActivityFile(traceabilityFile.getInputStream());
	}

}
