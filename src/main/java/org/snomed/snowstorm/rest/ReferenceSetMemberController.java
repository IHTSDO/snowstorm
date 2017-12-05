package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class ReferenceSetMemberController {

	@Autowired
	private ReferenceSetMemberService memberService;

	@RequestMapping(value = "/{branch}/members", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
														   @RequestParam(required = false) String referenceSet,
														   @RequestParam(required = false) String referencedComponentId,
														   @RequestParam(required = false) Boolean active,
														   @RequestParam(required = false) String targetComponent,
														   @RequestParam(defaultValue = "0") int page,
														   @RequestParam(defaultValue = "50") int size) {
		return new ItemsPage<>(
				memberService.findMembers(
						BranchPathUriUtil.parseBranchPath(branch),
						active,
						referenceSet,
						referencedComponentId,
						targetComponent,
						new PageRequest(page, size)));
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ReferenceSetMember fetchMember(@PathVariable String branch,
										  @PathVariable String uuid) {
		return ControllerHelper.throwIfNotFound("Member", memberService.findMember(BranchPathUriUtil.parseBranchPath(branch), uuid));
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(@PathVariable String branch,
							 @PathVariable String uuid) {
		memberService.deleteMember(BranchPathUriUtil.parseBranchPath(branch), uuid);
	}

}
