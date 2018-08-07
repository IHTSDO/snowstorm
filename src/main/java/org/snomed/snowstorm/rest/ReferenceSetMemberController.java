package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Refset Members", description = "-")
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
														   @RequestParam(defaultValue = "0") int offset,
														   @RequestParam(defaultValue = "50") int limit) {
		return new ItemsPage<>(
				memberService.findMembers(
						BranchPathUriUtil.decodePath(branch),
						active,
						referenceSet,
						referencedComponentId,
						targetComponent,
						ControllerHelper.getPageRequest(offset, limit)));
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ReferenceSetMember fetchMember(@PathVariable String branch,
										  @PathVariable String uuid) {
		return ControllerHelper.throwIfNotFound("Member", memberService.findMember(BranchPathUriUtil.decodePath(branch), uuid));
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(@PathVariable String branch,
							 @PathVariable String uuid) {
		memberService.deleteMember(BranchPathUriUtil.decodePath(branch), uuid);
	}

}
