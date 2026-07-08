package org.snomed.snowstorm.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.services.WebRoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@Tag(name = "Web Route", description = "-")
public class WebRouteController {

	@Autowired
	WebRoutingService webRoutingService;

	@Operation(summary = "Issue 302 redirection based on locally configured web routing",
					description = "Swagger will attempt to follow the 302 redirection, so use developer's tools network tab to view the redirection issued.")
	@GetMapping(value = "/web-route")
	@CrossOrigin
	public ResponseEntity<Void> issueRedirect(@RequestParam String uri,
			@RequestParam(required = false) String _format,
			@RequestHeader(value = "Accept", required = false) String acceptHeader) throws URISyntaxException {
		// IllegalArgumentException is intentionally left to propagate to RestControllerAdvice, which renders it
		// as JSON. Reflecting the raw, attacker-supplied uri in a String response here previously allowed the
		// request's Accept header to make Spring emit Content-Type: text/html, turning it into a reflected XSS.
		String redirectionStr = webRoutingService.determineRedirectionString(uri, acceptHeader, _format);
		HttpHeaders headers = new HttpHeaders();
		headers.add("Access-Control-Allow-Headers", "x-requested-with, Content-Type");
		headers.setLocation(new URI(redirectionStr));
		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}

}
