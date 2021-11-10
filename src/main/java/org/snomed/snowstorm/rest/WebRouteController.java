package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import java.net.URI;
import java.net.URISyntaxException;

import org.snomed.snowstorm.core.data.services.WebRoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Web Route", description = "-")
public class WebRouteController {

	@Autowired
	WebRoutingService webRoutingService;

	@ApiOperation(value = "Issue 302 redirection based on locally configured web routing", 
					notes = "Swagger will attempt to follow the 302 redirection, so use developer's tools network tab to view the redirection issued.")
	@RequestMapping(value = "/web-route", method = RequestMethod.GET)
	@CrossOrigin
	public ResponseEntity<?> issueRedirect(@RequestParam String uri,
			@RequestParam(required = false) String _format,
			@RequestHeader(value = "Accept", required = false) String acceptHeader) throws URISyntaxException {
		try {
			String redirectionStr = webRoutingService.determineRedirectionString(uri, acceptHeader, _format);
			HttpHeaders headers = new HttpHeaders();
			headers.add("Access-Control-Allow-Headers", "x-requested-with, Content-Type");
			headers.setLocation(new URI(redirectionStr));
			return new ResponseEntity<Void>(headers,HttpStatus.FOUND);
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
		}
	}

}
