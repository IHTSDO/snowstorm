package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Api(tags = "Refset Members", description = "-")
@RequestMapping(produces = "application/json")
public class ReferenceSetMemberController {

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@ApiOperation(value = "Search for reference set members.",
			notes = "The referenceSet parameter is used to search for members of that reference set. " +
					"This parameter can be a concept identifier or an ECL expression, for example '<900000000000522004'.")
	@RequestMapping(value = "/{branch}/members", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
			@RequestParam(required = false) String referenceSet,
			@RequestParam(required = false) String referencedComponentId,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String targetComponent,
			@RequestParam(required = false) String mapTarget,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		Page<ReferenceSetMember> members = memberService.findMembers(
				BranchPathUriUtil.decodePath(branch),
				active,
				referenceSet,
				referencedComponentId,
				targetComponent,
				mapTarget,
				ControllerHelper.getPageRequest(offset, limit)
		);
		joinReferencedComponents(members.getContent(), ControllerHelper.getLanguageCodes(acceptLanguageHeader), branch);
		return new ItemsPage<>(members);
	}

	private void joinReferencedComponents(List<ReferenceSetMember> members, List<String> languageCodes, String branch) {
		Set<String> conceptIds = members.stream().map(ReferenceSetMember::getReferencedComponentId).filter(IdentifierService::isConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(BranchPathUriUtil.decodePath(branch), conceptIds, languageCodes).getResultsMap();
		members.forEach(member -> {
			ConceptMini conceptMini = conceptMinis.get(member.getReferencedComponentId());
			if (conceptMini != null) {
				member.setReferencedComponent(conceptMini);
			}
		});
	}


	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ReferenceSetMember fetchMember(@PathVariable String branch,
			@PathVariable String uuid,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ReferenceSetMember member = memberService.findMember(BranchPathUriUtil.decodePath(branch), uuid);
		ControllerHelper.throwIfNotFound("Member", member);
		joinReferencedComponents(Collections.singletonList(member), ControllerHelper.getLanguageCodes(acceptLanguageHeader), branch);
		return member;
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(@PathVariable String branch,
							 @PathVariable String uuid) {
		memberService.deleteMember(BranchPathUriUtil.decodePath(branch), uuid);
	}

}
