package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.SerializationUtils;
import org.elasticsearch.common.util.set.Sets;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMemberView;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.AsyncRefsetMemberChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.RefSetMemberPageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@Tag(name = "Refset Members", description = "-")
@RequestMapping(produces = "application/json")
public class ReferenceSetMemberController {

	private static final Sort SORT_BY_MEMBER_ID_DESC = Sort.sort(ReferenceSetMember.class).by(ReferenceSetMember::getMemberId).descending();

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Operation(description = "Search for reference set ids.")
	@GetMapping(value = "/browser/{branch}/members")
	public RefSetMemberPageWithBucketAggregations<ReferenceSetMember> findBrowserReferenceSetMembersWithAggregations(
			@PathVariable String branch,
			@Parameter(description = "A reference set identifier or ECL expression can be used to limit the reference sets searched. Example: <723564002")
			@RequestParam(required = false) String referenceSet,
			@Parameter(description = "A concept identifier or ECL expression can be used to limit the modules searched. Example: <900000000000445007")
			@RequestParam(required = false) String module,
			@Parameter(description = "Set of referencedComponentId ids to limit search")
			@RequestParam(required = false) Set<String> referencedComponentId, //Ideally this would be plural, but that would break backwards compatibility
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "10") int limit,
			@RequestParam(required = false) String searchAfter,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);
		PageRequest pageRequest = ControllerHelper.getPageRequest(offset, limit, SORT_BY_MEMBER_ID_DESC, searchAfter);

		TimerUtil timer = new TimerUtil("Member aggregation debug " + branch);
		// Find Reference Sets with aggregation
		MemberSearchRequest searchRequest = new MemberSearchRequest()
				.active(active)
				.referenceSet(referenceSet)
				.module(module)
				.referencedComponentIds(referencedComponentId);
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
				new RefSetMemberPageWithBucketAggregations<>(page.getContent(), page.getPageable(), page.getTotalElements(), page.getBuckets().get("memberCountsByReferenceSet"), page.getSearchAfterArray());
		pageWithBucketAggregations.setReferenceSets(referenceSets);
		timer.finish();
		return pageWithBucketAggregations;
	}

	@Operation(summary = "Search for reference set members.")
	@GetMapping(value = "/{branch}/members")
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
			@Parameter(description = "A reference set identifier or ECL expression can be used to limit the reference sets searched. Example: <723564002")
			@RequestParam(required = false) String referenceSet,
			@Parameter(description = "A concept identifier or ECL expression can be used to limit the modules searched. Example: <900000000000445007")
			@RequestParam(required = false) String module,
			@Parameter(description = "Set of referencedComponentId ids to limit search")
			@RequestParam(required = false) Set<String> referencedComponentId, //Ideally this would be plural, but that would break backwards compatibility
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Boolean isNullEffectiveTime,
			@Parameter(description = "Set of target component ids to limit search")
			@RequestParam(required = false) Set<String> targetComponent,
			@RequestParam(required = false) String mapTarget,
			@Parameter(description = "Search by concept identifiers within an owlExpression.")
			@RequestParam(name = "owlExpression.conceptId", required = false) String owlExpressionConceptId,
			@Parameter(description = "Return axiom members with a GCI owlExpression.")
			@RequestParam(name = "owlExpression.gci", required = false) Boolean owlExpressionGCI,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
		   	@RequestParam(required = false) String searchAfter,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ControllerHelper.validatePageSize(offset, limit);
		branch = BranchPathUriUtil.decodePath(branch);
		Page<ReferenceSetMember> members = memberService.findMembers(
				branch,
				new MemberSearchRequest()
						.active(active)
						.isNullEffectiveTime(isNullEffectiveTime)
						.referenceSet(referenceSet)
						.module(module)
						.referencedComponentIds(referencedComponentId)
						.targetComponentIds(targetComponent)
						.mapTarget(mapTarget)
						.owlExpressionConceptId(owlExpressionConceptId)
						.owlExpressionGCI(owlExpressionGCI)
				,
				ControllerHelper.getPageRequest(offset, limit, SORT_BY_MEMBER_ID_DESC, searchAfter)
		);
		joinReferencedComponents(members.getContent(), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return new ItemsPage<>(members);
	}
	
	@Operation(summary = "Search for reference set members using bulk filters")
	@PostMapping(value = "/{branch}/members/search")
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
			@RequestBody MemberSearchRequest memberSearchRequest,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ControllerHelper.validatePageSize(offset, limit);
		branch = BranchPathUriUtil.decodePath(branch);
		Page<ReferenceSetMember> members = memberService.findMembers(
				branch,
				memberSearchRequest,
				ControllerHelper.getPageRequest(offset, limit)
		);
		joinReferencedComponents(members.getContent(), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return new ItemsPage<>(members);
	}


	private void joinReferencedComponents(List<ReferenceSetMember> members, List<LanguageDialect> languageDialects, String branch) {
		Set<String> conceptIds = members.stream().map(ReferenceSetMember::getReferencedComponentId).filter(IdentifierService::isConceptId).collect(Collectors.toSet());
		Set<String> descriptionIds = members.stream().map(ReferenceSetMember::getReferencedComponentId).filter(IdentifierService::isDescriptionId).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds, languageDialects).getResultsMap();

		Map<String, Description> descriptions;
		if (!descriptionIds.isEmpty()) {
			Page<Description> descriptionsPage = descriptionService.findDescriptions(branch, null, descriptionIds, null, PageRequest.of(0, descriptionIds.size()));
			descriptions = descriptionsPage.stream().collect(Collectors.toMap(Description::getId, Function.identity()));
		} else {
			descriptions = new HashMap<>();
		}

		members.forEach(member -> {
			ConceptMini conceptMini = conceptMinis.get(member.getReferencedComponentId());
			if (conceptMini != null) {
				member.setReferencedComponentConceptMini(conceptMini);
			}

			Description description = descriptions.get(member.getReferencedComponentId());
			if (description != null) {
				member.setReferencedComponentSnomedComponent(description);
			}
		});
	}


	@GetMapping(value = "/{branch}/members/{uuid}")
	@JsonView(value = View.Component.class)
	public ReferenceSetMember fetchMember(@PathVariable String branch,
			@PathVariable String uuid,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		ReferenceSetMember member = memberService.findMember(branch, uuid);
		ControllerHelper.throwIfNotFound("Member", member);
		joinReferencedComponents(Collections.singletonList(member), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return member;
	}

	@Operation(summary = "Create a reference set member.",
			description = "If the 'moduleId' is not set the '" + Config.DEFAULT_MODULE_ID_KEY + "' will be used from branch metadata (resolved recursively).")
	@PostMapping(value = "/{branch}/members")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ResponseEntity<ReferenceSetMemberView> createMember(@PathVariable String branch, @RequestBody @Valid ReferenceSetMemberView member) {
		ControllerHelper.requiredParamConceptIdFormat(member.getRefsetId(), "refsetId");
		ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		branch = BranchPathUriUtil.decodePath(branch);

		ReferenceSetMember createdMember = memberService.createMember(branch, (ReferenceSetMember) member);
		if (createdMember == null) {
			throw new IllegalStateException("Member creation failed. No object returned from member service.");
		}
		return new ResponseEntity<>(createdMember, ControllerHelper.getCreatedLocationHeaders(createdMember.getId()), HttpStatus.OK);
	}

	@Operation(summary = "Start a bulk reference set member create/update job.",
			description = "Reference set members can be created or updated using this endpoint. " +
					"Use the location header in the response to check the job status. " +
					"If the 'moduleId' is not set the '" + Config.DEFAULT_MODULE_ID_KEY + "' will be used from branch metadata (resolved recursively).")
	@PostMapping(value = "/{branch}/members/bulk")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ResponseEntity<Void> createUpdateMembersBulkChange(@PathVariable String branch, @RequestBody @Valid List<ReferenceSetMemberView> members) {

		branch = BranchPathUriUtil.decodePath(branch);
		final List<ReferenceSetMember> refsetMembers = members.stream().map(ReferenceSetMember.class::cast).collect(Collectors.toList());

		// Basic validation
		for (ReferenceSetMember member : refsetMembers) {
			ControllerHelper.requiredParamConceptIdFormat(member.getRefsetId(), "refsetId");
			ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		}

		String batchId = memberService.newCreateUpdateAsyncJob();
		memberService.createUpdateAsync(batchId, branch, refsetMembers, SecurityContextHolder.getContext());
		return ControllerHelper.getCreatedResponse(batchId);
	}

	@Operation(summary = "Fetch the status of a bulk reference set member create/update job.")
	@GetMapping(value = "/{branch}/members/bulk/{bulkChangeId}")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public AsyncRefsetMemberChangeBatch getMemberBulkChange(@PathVariable String branch, @PathVariable String bulkChangeId) {
		return ControllerHelper.throwIfNotFound("Bulk Change", memberService.getBatchChange(bulkChangeId));
	}

	@Operation(summary = "Update a reference set member.")
	@PutMapping(value = "/{branch}/members/{uuid}")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ReferenceSetMemberView updateMember(@PathVariable String branch,
												 @PathVariable String uuid,
												 @RequestBody ReferenceSetMemberView member) {

		ControllerHelper.requiredParamConceptIdFormat(member.getRefsetId(), "refsetId");
		ControllerHelper.requiredParam(member.getReferencedComponentId(), "referencedComponentId");
		branch = BranchPathUriUtil.decodePath(branch);

		ReferenceSetMember toUpdate = (ReferenceSetMember) member;
		toUpdate.setMemberId(uuid);
		return memberService.updateMember(branch, toUpdate);
	}

	@Operation(summary = "Delete a reference set member.")
	@DeleteMapping(value = "/{branch}/members/{uuid}")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(
			@PathVariable String branch,
			@PathVariable String uuid,
			@Parameter(description = "Force the deletion of a released member.")
			@RequestParam(defaultValue = "false") boolean force) {

		memberService.deleteMember(BranchPathUriUtil.decodePath(branch), uuid, force);
	}

	@Operation(summary = "Batch delete reference set members.")
	@DeleteMapping(value = "/{branch}/members")
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMembers(
			@PathVariable String branch,
			@RequestBody MemberIdsPojo memberIdsPojo,
			@Parameter(description = "Force the deletion of released members.")
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
