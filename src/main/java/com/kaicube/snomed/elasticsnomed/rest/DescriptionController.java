package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping("/**/descriptions")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<Description> findConcepts(@RequestParam String term,
			@RequestParam(defaultValue = "0") int number, @RequestParam(defaultValue = "100") int size,
			HttpServletRequest request) {
		final String path = ControllerHelper.extractBranchPath(request, "/descriptions");
		return new Page<>(conceptService.findDescriptions(path, term, new PageRequest(number, size)));
	}

}
