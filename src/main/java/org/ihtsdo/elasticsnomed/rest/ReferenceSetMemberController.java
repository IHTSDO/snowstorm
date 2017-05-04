package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.domain.*;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.ihtsdo.elasticsnomed.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
public class ReferenceSetMemberController {

	@Autowired
	private ReferenceSetMemberService memberService;

	@RequestMapping(value = "/{branch}/members", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ReferenceSetMember> findRefsetMembers(@PathVariable String branch,
														   @RequestParam String targetComponent,
														   @RequestParam(defaultValue = "0") int page,
														   @RequestParam(defaultValue = "50") int size) {
		return new ItemsPage<>(memberService.findMembers(BranchPathUriUtil.parseBranchPath(branch), targetComponent, new PageRequest(page, size)));
	}

	@RequestMapping(value = "/{branch}/members/{uuid}", method = RequestMethod.DELETE)
	@JsonView(value = View.Component.class)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMember(@PathVariable String branch,
							 @PathVariable String uuid) {
		memberService.deleteMember(BranchPathUriUtil.parseBranchPath(branch), uuid);
	}

}
