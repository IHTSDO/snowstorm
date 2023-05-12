package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Identifier;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.IdentifierComponentService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.IdentifierSearchRequest;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@Tag(name = "Identifiers", description = "-")
@RequestMapping(produces = "application/json")
public class IdentifierController {

	@Autowired
	private IdentifierComponentService identifierComponentService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@GetMapping(value = "/{branch}/identifiers", produces = {"application/json", "text/csv"})
	@JsonView(value = View.Component.class)
	public ItemsPage<Identifier> findIdentifiers(
			@PathVariable String branch,
			@RequestParam(required = false) String alternateIdentifier,
			@RequestParam(required = false) String identifierSchemeId,
			@RequestParam(required = false) Boolean activeFilter,
			@RequestParam(required = false) Boolean isNullEffectiveTime,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) Set<String> referencedComponentIds,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit,
			@RequestParam(required = false) String searchAfter,
			@Parameter(description = "Accept-Language header can take the format en-x-900000000000508004 which sets the language reference set to use in the results.")
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ControllerHelper.validatePageSize(offset, limit);
		branch = BranchPathUriUtil.decodePath(branch);
		Page<Identifier> identifiers = identifierComponentService.findIdentifiers(
				branch,
				new IdentifierSearchRequest()
						.active(activeFilter)
						.isNullEffectiveTime(isNullEffectiveTime)
						.module(module)
						.alternateIdentifier(alternateIdentifier)
						.identifierSchemeId(identifierSchemeId)
						.referencedComponentIds(referencedComponentIds)
				,
				ControllerHelper.getPageRequest(offset, limit, null, searchAfter)
		);
		joinReferencedComponents(identifiers.getContent(), ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branch);
		return new ItemsPage<>(identifiers);
	}

	private void joinReferencedComponents(List<Identifier> identifiers, List<LanguageDialect> languageDialects, String branch) {
		Set<String> conceptIds = identifiers.stream().map(Identifier::getReferencedComponentId).filter(IdentifierService::isConceptId).collect(Collectors.toSet());
		Set<String> descriptionIds = identifiers.stream().map(Identifier::getReferencedComponentId).filter(IdentifierService::isDescriptionId).collect(Collectors.toSet());
		Map <String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, conceptIds, languageDialects).getResultsMap();

		Map<String, Description> descriptions;
		if (!descriptionIds.isEmpty()) {
			Page<Description> descriptionsPage = descriptionService.findDescriptions(branch, null, descriptionIds, null, PageRequest.of(0, descriptionIds.size()));
			descriptions = descriptionsPage.stream().collect(Collectors.toMap(Description::getId, Function.identity()));
		} else {
			descriptions = new HashMap <>();
		}

		identifiers.forEach(identifier -> {
			ConceptMini conceptMini = conceptMinis.get(identifier.getReferencedComponentId());
			if (conceptMini != null) {
				identifier.setReferencedComponentConceptMini(conceptMini);
			}

			Description description = descriptions.get(identifier.getReferencedComponentId());
			if (description != null) {
				identifier.setReferencedComponentSnomedComponent(description);
			}
		});
	}
}
