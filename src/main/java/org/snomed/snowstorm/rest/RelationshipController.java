package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Sets;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.RelationshipService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Api(tags = "Relationships", description = "-")
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

		branch = BranchPathUriUtil.decodePath(branch);
		Page<Relationship> relationshipPage = relationshipService.findRelationships(
				branch,
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

		expandSourceAndTarget(branch, relationshipPage.getContent());

		return new ItemsPage<>(relationshipPage);
	}

	private void expandSourceAndTarget(String branch, List<Relationship> relationships) {
		Set<String> sourceIds = relationships.stream().map(Relationship::getSourceId).collect(Collectors.toSet());
		Set<String> typeIds = relationships.stream().map(Relationship::getTypeId).collect(Collectors.toSet());

		Map<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, Sets.union(sourceIds, typeIds)).getResultsMap();
		relationships.forEach(r -> {
			r.setSource(conceptMinis.get(r.getSourceId()));
			r.setType(conceptMinis.get(r.getTypeId()));
		});
	}

	@RequestMapping(value = "{branch}/relationship/{relationshipId}", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Relationship fetchRelationship(@PathVariable String branch, @PathVariable String relationshipId) {
		branch = BranchPathUriUtil.decodePath(branch);
		Relationship relationship = relationshipService.findRelationship(BranchPathUriUtil.decodePath(branch), relationshipId);
		if (relationship != null) {
			expandSourceAndTarget(branch, Collections.singletonList(relationship));
		}
		return ControllerHelper.throwIfNotFound("Relationship", relationship);
	}

}
