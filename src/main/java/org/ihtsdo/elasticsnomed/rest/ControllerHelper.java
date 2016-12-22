package org.ihtsdo.elasticsnomed.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class ControllerHelper {

	public static String parseBranchPath(String branch) {
		return branch.replace("|", "/");
	}

	public static ResponseEntity<Object> getCreatedResponse(String id) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder
				.fromCurrentRequest().path("/{id}")
				.buildAndExpand(id).toUri());
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

}
