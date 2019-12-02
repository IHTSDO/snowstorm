package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.rest.pojo.BuildVersion;
import org.snomed.snowstorm.rest.pojo.CreateCodeSystemVersionRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Version", description = "Build Version")
public class VersionController {

	@Autowired
	private BuildProperties buildProperties;

	@ApiOperation("Software build version and timestamp.")
	@RequestMapping(value = "/version", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public BuildVersion getBuildInformation() {
		return new BuildVersion(buildProperties.getVersion(), buildProperties.getTime().toString());
	}

}
