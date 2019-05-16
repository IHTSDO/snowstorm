package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMemberView;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Api(tags = "Refset Members", description = "-")
@RequestMapping(produces = "application/json")
public class ReferenceSetMemberController {

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@ApiOperation("Search for reference set ids")
	@RequestMapping(value = "/browser/{branch}/members", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public PageWithBucketAggregations<ReferenceSetMember> findBrowserReferenceSetMembersWithAggregations(
			@PathVariable String branch,
			@RequestParam(required = false) Boolean activeMember,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "10") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		String path = BranchPathUriUtil.decodePath(branch);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);
		PageWithBucketAggregations<ReferenceSetMember> page = memberService.findReferenceSetMembersWithAggregations(path, pageRequest, activeMember);

		 Map<String, Map<String, Long>> buckets = page.getBuckets();
		List<String> languageCodes = ControllerHelper.getLanguageCodes(acceptLanguageHeader);
		 Set<String> conceptIds = new HashSet<>();
		 for (String key : buckets.keySet()) {
		 	conceptIds.addAll(buckets.get(key).keySet());
		 }
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(path, conceptIds, languageCodes).getResultsMap();

		PageWithBucketAggregations<ReferenceSetMember> pageWithBucketAggregations = new PageWithBucketAggregations<>(page.getContent(), page.getPageable(), page.getTotalElements(), page.getBuckets());
		pageWithBucketAggregations.setBucketConcepts(conceptMinis);
		return pageWithBucketAggregations;
	}

	@ApiOperation("Search for reference set members.")
	@RequestMapping(value = "/{branch}/members", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
			@ApiParam("A reference set identifier or ECL expression can be used to limit the reference sets searched. Example: <723564002")
			@RequestParam(required = false) String referenceSet,
			@RequestParam(required = false) String referencedComponentId,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String targetComponent,
			@RequestParam(required = false) String mapTarget,
			@ApiParam("Search by concept identifiers within an owlExpression.")
			@RequestParam(name = "owlExpression.conceptId", required = false) String owlExpressionConceptId,
			@ApiParam("Return axiom members with a GCI owlExpression.")
			@RequestParam(name = "owlExpression.gci", required = false) Boolean owlExpressionGCI,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		Page<ReferenceSetMember> members = memberService.findMembers(
				BranchPathUriUtil.decodePath(branch),
				new MemberSearchRequest()
					.active(active)
					.referenceSet(referenceSet)
					.referencedComponentId(referencedComponentId)
					.targetComponentId(targetComponent)
					.mapTarget(mapTarget)
					.owlExpressionConceptId(owlExpressionConceptId)
					.owlExpressionGCI(owlExpressionGCI)
				,
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

	@RequestMapping(value = "/{branch}/members", method = RequestMethod.POST)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ReferenceSetMemberView createMember(@PathVariable String branch, @RequestBody @Valid ReferenceSetMemberView member) {
		ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		ControllerHelper.requiredParam(member.getRefsetId(), "refsetId");
		return memberService.createMember(BranchPathUriUtil.decodePath(branch), (ReferenceSetMember) member);
	}

	@ApiOperation("Update a reference set member")
	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.PUT)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ReferenceSetMemberView updateMember(@PathVariable String branch,
												 @PathVariable String uuid,
												 @RequestBody ReferenceSetMemberView member) {

		ControllerHelper.requiredParam(member.getRefsetId(), "refsetId");
		ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		ReferenceSetMember toUpdate = (ReferenceSetMember) member;
		toUpdate.setMemberId(uuid);
		return memberService.updateMember(BranchPathUriUtil.decodePath(branch), toUpdate);
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(@PathVariable String branch,
							 @PathVariable String uuid,
							 @RequestParam(defaultValue = "false") boolean force) {

		memberService.deleteMember(BranchPathUriUtil.decodePath(branch), uuid, force);
	}
}
