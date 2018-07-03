package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.RelationshipService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class RelationshipController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

	public enum RelationshipCharacteristicType {
		STATED_RELATIONSHIP, INFERRED_RELATIONSHIP, ADDITIONAL_RELATIONSHIP;

		private Relationship.CharacteristicType getCharacteristicType() {
			if (this == STATED_RELATIONSHIP) {
				return Relationship.CharacteristicType.stated;
			} else if (this == INFERRED_RELATIONSHIP) {
				return Relationship.CharacteristicType.inferred;
			} else {
				return Relationship.CharacteristicType.additional;
			}
		}
	}

	@RequestMapping(value = "{branch}/relationships", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<Relationship> findRelationships(@PathVariable String branch,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String effectiveTime,
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String destination,
			@RequestParam(required = false) RelationshipCharacteristicType characteristicType,
			@RequestParam(required = false) Integer group,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "50") int limit) {

		Page<Relationship> relationshipPage = relationshipService.findRelationships(
				BranchPathUriUtil.decodePath(branch),
				null,
				active,
				module,
				effectiveTime,
				source,
				type,
				destination,
				characteristicType != null ? characteristicType.getCharacteristicType() : null,
				group,
				ControllerHelper.getPageRequest(offset, limit));

		relationshipPage.getContent().forEach(r -> {
			r.getSource().nestFsn();
			r.getType().nestFsn();
		});

		return new ItemsPage<>(relationshipPage);
	}

	@RequestMapping(value = "{branch}/relationship/{relationshipId}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Relationship fetchDescription(@PathVariable String branch, @PathVariable String relationshipId) {
		return ControllerHelper.throwIfNotFound("Relationship", relationshipService.fetchRelationship(BranchPathUriUtil.decodePath(branch), relationshipId));
	}

}
