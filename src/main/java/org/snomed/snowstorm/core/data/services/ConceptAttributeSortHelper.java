package org.snomed.snowstorm.core.data.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SortOrderProperties;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

@Service
public class ConceptAttributeSortHelper {

	@Autowired
	private SortOrderProperties sortOrderProperties;

	@Autowired
	private QueryService queryService;

	private Map<String, Map<Long, Short>> domainAttributeOrderMap;

	private Map<String, String> subHierarchyToTopLevelTagCache = Collections.synchronizedMap(new HashMap<>());

	private static final Pattern TAG_PATTERN = Pattern.compile("^.*\\((.*)\\)$");
	private static final Set<String> EN_LANGUAGE_CODE = Collections.singleton("en");

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		domainAttributeOrderMap = sortOrderProperties.getDomainAttributeOrderMap();
	}

	private static final Comparator<Relationship> ACTIVE_RELATIONSHIP_COMPARATOR_WITH_GROUP_LAST = Comparator
			.comparing(Relationship::getAttributeOrder, Comparator.nullsLast(Short::compareTo))
			.thenComparing(Relationship::getTargetFsn, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getGroupId);

	private static final Comparator<Relationship> RELATIONSHIP_COMPARATOR = Comparator
			.comparing(Relationship::isActive).reversed()
			.thenComparing(Relationship::getGroupId)
			.thenComparing(Relationship::getAttributeOrder, Comparator.nullsLast(Short::compareTo))
			.thenComparing(Relationship::getTargetFsn, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getTypeId, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getDestinationId, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::hashCode);

	void sortAttributes(Iterable<Concept> concepts) {
		for (Concept concept : concepts) {
			String semanticTag = getEnSemanticTag(concept);
			if (semanticTag != null) {
				if (!domainAttributeOrderMap.containsKey(semanticTag)) {
					semanticTag = getTopLevelHierarchyTag(semanticTag, concept);
				}
				Map<Long, Short> attributeOrderMap = domainAttributeOrderMap.get(semanticTag);
				if (attributeOrderMap != null) {
					// Add sorting for Is a (attribute)
					attributeOrderMap.putAll(domainAttributeOrderMap.getOrDefault("all", Collections.emptyMap()));
					for (Axiom axiom : concept.getClassAxioms()) {
						axiom.setRelationships(getSortedRelationships(axiom.getRelationships(), attributeOrderMap, concept.getConceptId()));
					}
					for (Axiom axiom : concept.getGciAxioms()) {
						axiom.setRelationships(getSortedRelationships(axiom.getRelationships(), attributeOrderMap, concept.getConceptId()));
					}
					concept.setRelationships(getSortedRelationships(concept.getRelationships(), attributeOrderMap, concept.getConceptId()));
				}
			}
		}
	}

	private String getTopLevelHierarchyTag(String semanticTag, Concept concept) {
		if (!subHierarchyToTopLevelTagCache.containsKey(semanticTag)) {
			String statedParent = null;

			Set<Axiom> classAxioms = concept.getClassAxioms();
			if (!classAxioms.isEmpty()) {
				statedParent = getStatedParentId(classAxioms.iterator().next().getRelationships());
			}
			if (statedParent == null) {
				statedParent = getStatedParentId(concept.getRelationships().stream()
						.filter(r -> r.isActive() && Concepts.STATED_RELATIONSHIP.equals(r.getCharacteristicTypeId())).collect(Collectors.toSet()));
			}
			if (statedParent != null) {
				try {
					Page<ConceptMini> topLevelHierarchy = queryService.eclSearch("<!" + Concepts.SNOMEDCT_ROOT + " AND >" + statedParent, true, "MAIN", PageRequest.of(0, 1));
					if (topLevelHierarchy.getTotalElements() > 0) {
						String fsnTerm = topLevelHierarchy.getContent().get(0).getFsnTerm();
						String parentTag = getEnSemanticTag(fsnTerm);
						subHierarchyToTopLevelTagCache.put(semanticTag, parentTag);
					}
				} catch (IllegalArgumentException e) {
					logger.info("Could not sort attributes of concept {} because ECL to fetch the top level hierarchy failed.", concept.getId());
				}
			}
		}
		return subHierarchyToTopLevelTagCache.get(semanticTag);
	}

	private String getStatedParentId(Set<Relationship> relationships) {
		for (Relationship relationship : relationships) {
			if (relationship.isActive() && Concepts.ISA.equals(relationship.getTypeId())) {
				return relationship.getDestinationId();
			}
		}
		return null;
	}

	private String getEnSemanticTag(Concept concept) {
		TermLangPojo fsn = DescriptionHelper.getFsnDescriptionTermAndLang(concept.getDescriptions(), EN_LANGUAGE_CODE);
		return getEnSemanticTag(fsn.getTerm());
	}

	private String getEnSemanticTag(String term) {
		if (term != null) {
			Matcher matcher = TAG_PATTERN.matcher(term);
			if (matcher.matches()) {
				String tag = matcher.group(1);
				return tag.toLowerCase().replace(" ", "_");
			}
		}
		return null;
	}

	private Set<Relationship> getSortedRelationships(Set<Relationship> relationshipSet, Map<Long, Short> attributeOrderMap, String conceptId) {
		int originalSize = relationshipSet.size();

		List<Relationship> relationships = new ArrayList<>(relationshipSet);
		Set<Relationship> sortedUngroupedRelationships = new TreeSet<>(ACTIVE_RELATIONSHIP_COMPARATOR_WITH_GROUP_LAST);
		for (Relationship relationship : relationships) {
			relationship.setAttributeOrder(attributeOrderMap.get(parseLong(relationship.getTypeId())));
			if (relationship.isActive()) {
				sortedUngroupedRelationships.add(relationship);
			}
		}

		// Set group numbers using attribute sorting
		int group = 1;
		Map<Integer, Integer> oldGroupToNewGroupMap = new HashMap<>();
		for (Relationship relationship : sortedUngroupedRelationships) {
			int groupId = relationship.getGroupId();
			if (groupId != 0) {
				if (!oldGroupToNewGroupMap.keySet().contains(groupId)) {
					oldGroupToNewGroupMap.put(groupId, group++);
				}
			}
		}

		Set<Relationship> sortedRelationships = new TreeSet<>(RELATIONSHIP_COMPARATOR);
		for (Relationship relationship : relationships) {
			if (relationship.getGroupId() != 0 && oldGroupToNewGroupMap.containsKey(relationship.getGroupId())) {
				relationship.setGroupId(oldGroupToNewGroupMap.get(relationship.getGroupId()));
			}
			sortedRelationships.add(relationship);
		}

		if (sortedRelationships.size() != originalSize) {
			throw new IllegalStateException(String.format("Sorted attribute set is smaller than original set for conceptId %s. Do duplicates exist?", conceptId));
		}

		return sortedRelationships;
	}

	Map<String, String> getSubHierarchyToTopLevelTagCache() {
		return subHierarchyToTopLevelTagCache;
	}
}
