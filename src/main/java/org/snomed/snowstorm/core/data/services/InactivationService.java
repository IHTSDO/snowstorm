package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.rest.pojo.InactivationImpactResponse;
import org.snomed.snowstorm.rest.pojo.InactivationImpactResponse.InactivationImpactConcept;
import org.snomed.snowstorm.rest.pojo.InactivationReason;
import org.snomed.snowstorm.rest.pojo.InactivationReasonsResponse;
import org.snomed.snowstorm.rest.pojo.ValidAssociation;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;

/**
 * Server-side inactivation helpers: reason/association metadata and stated-form impact
 * for the authoring-ui inactivation review tabs.
 */
@Service
public class InactivationService {

	private static final int UNBOUNDED_MAX_TARGETS = 99;
	public static final String NO_ASSOCIATION_REQUIRED = "NO_ASSOCIATION_REQUIRED";

	private final ConceptService conceptService;
	private final DescriptionService descriptionService;
	private final ReferenceSetMemberService referenceSetMemberService;
	private final SemanticIndexService semanticIndexService;
	private final ECLQueryService eclQueryService;
	private final VersionControlHelper versionControlHelper;
	private final BranchService branchService;

	public InactivationService(ConceptService conceptService, DescriptionService descriptionService,
			ReferenceSetMemberService referenceSetMemberService, SemanticIndexService semanticIndexService,
			ECLQueryService eclQueryService, VersionControlHelper versionControlHelper, BranchService branchService) {
		this.conceptService = conceptService;
		this.descriptionService = descriptionService;
		this.referenceSetMemberService = referenceSetMemberService;
		this.semanticIndexService = semanticIndexService;
		this.eclQueryService = eclQueryService;
		this.versionControlHelper = versionControlHelper;
		this.branchService = branchService;
	}

	public InactivationReasonsResponse getInactivationReasons(String branchPath) {
		branchService.findBranchOrThrow(branchPath, true);
		return new InactivationReasonsResponse(conceptInactivationReasons(), descriptionInactivationReasons());
	}

	public InactivationImpactResponse getInactivationImpact(String branchPath, String conceptId,
			List<LanguageDialect> languageDialects) {

		if (!conceptService.exists(conceptId, branchPath)) {
			throw new NotFoundException("Concept '" + conceptId + "' not found on branch '" + branchPath + "'.");
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		Set<Long> childIds = selectConceptIds("<! " + conceptId, branchCriteria);
		Set<Long> inboundIds = selectConceptIds("*: * = " + conceptId, branchCriteria);
		Set<Long> attributeIds = new LinkedHashSet<>(inboundIds);
		attributeIds.removeAll(childIds);

		List<ReferenceSetMember> gciMembers = referenceSetMemberService.findMembers(branchPath,
				new MemberSearchRequest()
						.active(true)
						.owlExpressionConceptId(conceptId)
						.owlExpressionGCI(true),
				LARGE_PAGE).getContent();
		Set<Long> gciIds = gciMembers.stream()
				.map(ReferenceSetMember::getReferencedComponentId)
				.filter(Objects::nonNull)
				.map(Long::parseLong)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		List<ReferenceSetMember> historicalMembers = referenceSetMemberService.findMembers(branchPath,
				new MemberSearchRequest()
						.active(true)
						.referenceSet("<" + Concepts.REFSET_HISTORICAL_ASSOCIATION)
						.targetComponentIds(Set.of(conceptId)),
				LARGE_PAGE).getContent();

		Set<Long> parentLookupIds = new LinkedHashSet<>();
		parentLookupIds.add(Long.parseLong(conceptId));
		parentLookupIds.addAll(childIds);
		parentLookupIds.addAll(attributeIds);
		parentLookupIds.addAll(gciIds);

		Map<Long, QueryConcept> queryConcepts = semanticIndexService.loadQueryConcepts(branchPath, parentLookupIds, true);

		Set<String> descriptionIds = new HashSet<>();
		Set<Long> associationConceptIds = new LinkedHashSet<>();
		for (ReferenceSetMember member : historicalMembers) {
			String referencedComponentId = member.getReferencedComponentId();
			if (IdentifierService.isConceptId(referencedComponentId)) {
				associationConceptIds.add(Long.parseLong(referencedComponentId));
			} else if (IdentifierService.isDescriptionId(referencedComponentId)) {
				descriptionIds.add(referencedComponentId);
			}
		}

		Map<String, Description> descriptions = loadDescriptions(branchPath, descriptionIds);
		descriptions.values().stream()
				.map(Description::getConceptId)
				.filter(Objects::nonNull)
				.forEach(id -> associationConceptIds.add(Long.parseLong(id)));

		Set<Long> miniIds = new LinkedHashSet<>(parentLookupIds);
		miniIds.addAll(associationConceptIds);
		for (QueryConcept queryConcept : queryConcepts.values()) {
			if (queryConcept.getParents() != null) {
				miniIds.addAll(queryConcept.getParents());
			}
		}
		Map<String, ConceptMini> minis = conceptService.findConceptMinis(branchPath, miniIds, languageDialects).getResultsMap();

		List<InactivationImpactConcept> affectedChildren = childIds.stream()
				.map(id -> toImpactConcept(id, queryConcepts, minis))
				.sorted(impactComparator())
				.toList();
		List<InactivationImpactConcept> affectedAttributeConcepts = attributeIds.stream()
				.map(id -> toImpactConcept(id, queryConcepts, minis))
				.sorted(impactComparator())
				.toList();
		List<InactivationImpactConcept> affectedGcis = gciIds.stream()
				.map(id -> toImpactConcept(id, queryConcepts, minis))
				.sorted(impactComparator())
				.toList();
		joinReferencedComponents(historicalMembers, minis, descriptions);
		List<ReferenceSetMember> existingHistoricalAssociations = historicalMembers.stream()
				.sorted(Comparator.comparing(ReferenceSetMember::getReferencedComponentId, Comparator.nullsLast(String::compareTo)))
				.toList();

		Set<String> uniqueAffected = new LinkedHashSet<>();
		affectedChildren.forEach(c -> uniqueAffected.add(c.concept().getId()));
		affectedAttributeConcepts.forEach(c -> uniqueAffected.add(c.concept().getId()));
		affectedGcis.forEach(c -> uniqueAffected.add(c.concept().getId()));
		for (ReferenceSetMember member : existingHistoricalAssociations) {
			String referencedComponentId = member.getReferencedComponentId();
			if (IdentifierService.isConceptId(referencedComponentId)) {
				uniqueAffected.add(referencedComponentId);
			} else if (IdentifierService.isDescriptionId(referencedComponentId)) {
				Description description = descriptions.get(referencedComponentId);
				if (description != null && description.getConceptId() != null) {
					uniqueAffected.add(description.getConceptId());
				}
			}
		}

		return new InactivationImpactResponse(affectedChildren, affectedAttributeConcepts, affectedGcis,
				existingHistoricalAssociations, uniqueAffected.size());
	}

	private Set<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria) {
		Page<Long> page = eclQueryService.selectConceptIds(ecl, branchCriteria, true, LARGE_PAGE);
		return new LinkedHashSet<>(page.getContent());
	}

	private Map<String, Description> loadDescriptions(String branchPath, Set<String> descriptionIds) {
		if (descriptionIds.isEmpty()) {
			return Map.of();
		}
		return descriptionService.findDescriptions(branchPath, null, descriptionIds, null, LARGE_PAGE).getContent().stream()
				.collect(Collectors.toMap(Description::getDescriptionId, description -> description, (left, right) -> left));
	}

	private InactivationImpactConcept toImpactConcept(Long conceptId, Map<Long, QueryConcept> queryConcepts,
			Map<String, ConceptMini> minis) {
		return new InactivationImpactConcept(toConceptMicro(conceptId.toString(), minis),
				parentsOf(conceptId, queryConcepts, minis));
	}

	private static void joinReferencedComponents(List<ReferenceSetMember> members, Map<String, ConceptMini> minis,
			Map<String, Description> descriptions) {
		for (ReferenceSetMember member : members) {
			String referencedComponentId = member.getReferencedComponentId();
			ConceptMini conceptMini = minis.get(referencedComponentId);
			if (conceptMini != null) {
				member.setReferencedComponentConceptMini(conceptMini);
			}
			Description description = descriptions.get(referencedComponentId);
			if (description != null) {
				member.setReferencedComponentSnomedComponent(description);
			}
		}
	}

	private static List<ConceptMicro> parentsOf(Long conceptId, Map<Long, QueryConcept> queryConcepts, Map<String, ConceptMini> minis) {
		QueryConcept queryConcept = queryConcepts.get(conceptId);
		if (queryConcept == null || CollectionUtils.isEmpty(queryConcept.getParents())) {
			return List.of();
		}
		return queryConcept.getParents().stream()
				.sorted()
				.map(parentId -> toConceptMicro(parentId.toString(), minis))
				.toList();
	}

	private static ConceptMicro toConceptMicro(String conceptId, Map<String, ConceptMini> minis) {
		ConceptMini mini = minis.get(conceptId);
		return mini != null ? new ConceptMicro(mini) : new ConceptMicro(conceptId);
	}

	private static Comparator<InactivationImpactConcept> impactComparator() {
		return Comparator.comparing((InactivationImpactConcept impact) -> impact.concept().getTerm(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
				.thenComparing(impact -> impact.concept().getId(), Comparator.nullsLast(String::compareTo));
	}

	private static List<InactivationReason> conceptInactivationReasons() {
		return List.of(
				inactivationReason(Concepts.DUPLICATE, historicalAssociation(Concepts.REFSET_SAME_AS_ASSOCIATION, 1, 1, true)),
				inactivationReason(Concepts.AMBIGUOUS, historicalAssociation(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION, 1, UNBOUNDED_MAX_TARGETS, true)),
				inactivationReason(Concepts.ERRONEOUS, historicalAssociation(Concepts.REFSET_REPLACED_BY_ASSOCIATION, 1, 1, true)),
				inactivationReason(Concepts.OUTDATED,
						historicalAssociation(Concepts.REFSET_REPLACED_BY_ASSOCIATION, 1, 1, true),
						historicalAssociation(Concepts.REFSET_POSSIBLY_REPLACED_BY_ASSOCIATION, 1, UNBOUNDED_MAX_TARGETS, true),
						new ValidAssociation(NO_ASSOCIATION_REQUIRED, 0, 0, false)),
				inactivationReason(Concepts.CLASSIFICATION_DERIVED_COMPONENT,
						historicalAssociation(Concepts.REFSET_REPLACED_BY_ASSOCIATION, 1, 1, true),
						historicalAssociation(Concepts.REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION, 2, UNBOUNDED_MAX_TARGETS, true)),
				inactivationReason(Concepts.MEANING_OF_COMPONENT_UNKNOWN),
				inactivationReason(Concepts.NONCONFORMANCE_TO_EDITORIAL_POLICY,
						historicalAssociation(Concepts.REFSET_REPLACED_BY_ASSOCIATION, 1, 1, true),
						historicalAssociation(Concepts.REFSET_ALTERNATIVE_ASSOCIATION, 1, UNBOUNDED_MAX_TARGETS, true),
						new ValidAssociation(NO_ASSOCIATION_REQUIRED, 0, 0, false))
		);
	}

	private static List<InactivationReason> descriptionInactivationReasons() {
		return List.of(
				inactivationReason(Concepts.OUTDATED),
				inactivationReason(Concepts.GRAMMATICAL_DESCRIPTION_ERROR),
				inactivationReason(Concepts.NOT_SEMANTICALLY_EQUIVALENT, historicalAssociation(Concepts.REFSET_REFERS_TO_ASSOCIATION, 1, UNBOUNDED_MAX_TARGETS, true)),
				inactivationReason(Concepts.NONCONFORMANCE_TO_EDITORIAL_POLICY)
		);
	}

	private static InactivationReason inactivationReason(String id, ValidAssociation... associations) {
		String name = Concepts.inactivationIndicatorNames.get(id);
		if (name == null) {
			throw new IllegalStateException("Unknown inactivation indicator: " + id);
		}
		return new InactivationReason(id, name, inactivationReasonDisplayLabel(name), List.of(associations));
	}

	private static ValidAssociation historicalAssociation(String refsetId, int minTargets, int maxTargets, boolean targetsMustBeActive) {
		String type = Concepts.historicalAssociationNames.get(refsetId);
		if (type == null) {
			throw new IllegalStateException("Unknown historical association refset: " + refsetId);
		}
		return new ValidAssociation(type, minTargets, maxTargets, targetsMustBeActive);
	}

	private static String inactivationReasonDisplayLabel(String name) {
		if ("CONCEPT_NON_CURRENT".equals(name)) {
			return "Concept non-current";
		}
		String lower = name.replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
