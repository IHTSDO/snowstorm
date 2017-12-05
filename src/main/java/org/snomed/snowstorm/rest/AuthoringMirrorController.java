package org.snomed.snowstorm.rest;

import org.snomed.snowstorm.core.data.services.AuthoringMirrorService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(produces = "application/json")
public class AuthoringMirrorController {

	@Autowired
	private AuthoringMirrorService authoringMirrorService;

	@RequestMapping(value = "/authoring-mirror/activities", method = RequestMethod.POST)
	public void mirrorAuthoring(@RequestParam("traceability-file") MultipartFile traceabilityFile) throws IOException, ServiceException {
		authoringMirrorService.receiveActivityFile(traceabilityFile.getInputStream());
	}

}
