package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.lang3.SerializationUtils;
import org.elasticsearch.common.util.set.Sets;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMemberView;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.RefSetMemberPageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@ApiOperation("Search for reference set ids.")
	@RequestMapping(value = "/browser/{branch}/members", method = RequestMethod.GET)
	public RefSetMemberPageWithBucketAggregations<ReferenceSetMember> findBrowserReferenceSetMembersWithAggregations(
			@PathVariable String branch,
			@ApiParam("A reference set identifier or ECL expression can be used to limit the reference sets searched. Example: <723564002")
			@RequestParam(required = false) String referenceSet,
			@RequestParam(required = false) String referencedComponentId,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "10") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit);

		TimerUtil timer = new TimerUtil("Member aggregation debug " + branch);
		// Find Reference Sets with aggregation
		MemberSearchRequest searchRequest = new MemberSearchRequest()
				.active(active)
				.referenceSet(referenceSet)
				.referencedComponentId(referencedComponentId);
		PageWithBucketAggregations<ReferenceSetMember> page = memberService.findReferenceSetMembersWithAggregations(branch, pageRequest, searchRequest);
		timer.checkpoint("aggregation");

		Set<String> referenceSetIds = page.getBuckets().get("memberCountsByReferenceSet").keySet();

		// Find refset type
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		Map<String, String> refsetTypes = memberService.findRefsetTypes(referenceSetIds, branchCriteria, branch);
		timer.checkpoint("load types (" + referenceSetIds.size() + ")");

		// Load concept minis
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, Sets.union(referenceSetIds, new HashSet<>(refsetTypes.values())), languageDialects).getResultsMap();
		Map<String, ConceptMini> referenceSets = new HashMap<>();
		for (String referenceSetId : referenceSetIds) {
			ConceptMini refsetMini = conceptMinis.get(referenceSetId);
			if (refsetMini != null) {
				String type = refsetTypes.get(referenceSetId);
				if (type != null) {
					// If refset equals type then clone before adding field to prevent infinite recursion!
					ConceptMini typeConcept = conceptMinis.get(type);
					if (refsetMini == typeConcept) {
						typeConcept = SerializationUtils.clone(typeConcept);
					}
					refsetMini.addExtraField("referenceSetType", typeConcept);
				}
				referenceSets.put(referenceSetId, refsetMini);
			}
		}
		timer.checkpoint("Load minis");

		RefSetMemberPageWithBucketAggregations<ReferenceSetMember> pageWithBucketAggregations =
				new RefSetMemberPageWithBucketAggregations<>(page.getContent(), page.getPageable(), page.getTotalElements(), page.getBuckets().get("memberCountsByReferenceSet"));
		pageWithBucketAggregations.setReferenceSets(referenceSets);
		timer.finish();
		return pageWithBucketAggregations;
	}

	@ApiOperation("Search for reference set members.")
	@RequestMapping(value = "/{branch}/members", method = RequestMethod.GET)
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
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

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
		joinReferencedComponents(members.getContent(), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return new ItemsPage<>(members);
	}

	private void joinReferencedComponents(List<ReferenceSetMember> members, List<LanguageDialect> languageDialects, String branch) {
		Set<String> conceptIds = members.stream().map(ReferenceSetMember::getReferencedComponentId).filter(IdentifierService::isConceptId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(BranchPathUriUtil.decodePath(branch), conceptIds, languageDialects).getResultsMap();
		members.forEach(member -> {
			ConceptMini conceptMini = conceptMinis.get(member.getReferencedComponentId());
			if (conceptMini != null) {
				member.setReferencedComponent(conceptMini);
			}
		});
	}


	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ReferenceSetMember fetchMember(@PathVariable String branch,
			@PathVariable String uuid,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ReferenceSetMember member = memberService.findMember(BranchPathUriUtil.decodePath(branch), uuid);
		ControllerHelper.throwIfNotFound("Member", member);
		joinReferencedComponents(Collections.singletonList(member), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return member;
	}

	@ApiOperation("Create a reference set member.")
	@RequestMapping(value = "/{branch}/members", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ResponseEntity<ReferenceSetMemberView> createMember(@PathVariable String branch, @RequestBody @Valid ReferenceSetMemberView member) {
		ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		ControllerHelper.requiredParam(member.getRefsetId(), "refsetId");
		ReferenceSetMember createdMember = memberService.createMember(BranchPathUriUtil.decodePath(branch), (ReferenceSetMember) member);
		if (createdMember == null) {
			throw new IllegalStateException("Member creation failed. No object returned from member service.");
		}
		return new ResponseEntity<>(createdMember, ControllerHelper.getCreatedLocationHeaders(createdMember.getId()), HttpStatus.OK);
	}

	@ApiOperation("Update a reference set member.")
	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
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

	@ApiOperation("Delete a reference set member.")
	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(
			@PathVariable String branch,
			@PathVariable String uuid,
			@ApiParam("Force the deletion of a released member.")
			@RequestParam(defaultValue = "false") boolean force) {

		memberService.deleteMember(BranchPathUriUtil.decodePath(branch), uuid, force);
	}

	@ApiOperation("Batch delete reference set members.")
	@RequestMapping(value = "/{branch}/members", method = RequestMethod.DELETE)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMembers(
			@PathVariable String branch,
			@RequestBody MemberIdsPojo memberIdsPojo,
			@ApiParam("Force the deletion of released members.")
			@RequestParam(defaultValue = "false") boolean force) {

		memberService.deleteMembers(BranchPathUriUtil.decodePath(branch), memberIdsPojo.getMemberIds(), force);
	}

	public static class MemberIdsPojo {

		private Set<String> memberIds;

		public Set<String> getMemberIds() {
			return memberIds;
		}

		public void setMemberIds(Set<String> memberIds) {
			this.memberIds = memberIds;
		}
	}
}
