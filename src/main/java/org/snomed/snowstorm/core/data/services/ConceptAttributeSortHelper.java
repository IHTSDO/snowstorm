package org.snomed.snowstorm.core.data.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SortOrderProperties;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
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

	private static final int NEXT_GROUP_DEFAULT = 50;

	@Autowired
	private SortOrderProperties sortOrderProperties;

	@Autowired
	private QueryService queryService;

	private Map<String, Map<Long, Short>> domainAttributeOrderMap;

	private Map<String, String> subHierarchyToTopLevelTagCache = Collections.synchronizedMap(new HashMap<>());

	private static final Pattern TAG_PATTERN = Pattern.compile("^.*\\((.*)\\)$");
	private static final List<LanguageDialect> EN_LANGUAGE_DIALECT = Collections.singletonList(new LanguageDialect("en"));

	private Logger logger = LoggerFactory.getLogger(getClass());

	private static final Comparator<Relationship> ACTIVE_RELATIONSHIP_COMPARATOR_WITH_GROUP_LAST = Comparator
			.comparing(Relationship::getAttributeOrder, Comparator.nullsLast(Short::compareTo))
			.thenComparing(Relationship::getTargetFsn, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getGroupOrder);

	private static final Comparator<Relationship> RELATIONSHIP_COMPARATOR = Comparator
			.comparing(Relationship::isActive).reversed()
			.thenComparing(Relationship::getGroupOrder)
			.thenComparing(Relationship::getAttributeOrder, Comparator.nullsLast(Short::compareTo))
			.thenComparing(Relationship::getTargetFsn, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getTypeId, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::getDestinationId, Comparator.nullsLast(String::compareTo))
			.thenComparing(Relationship::hashCode);

	private static final Comparator<Axiom> AXIOM_COMPARATOR = (a, b) -> {
		Set<Relationship> relsA = a.getRelationships();
		Set<Relationship> relsB = b.getRelationships();
		if (relsA.isEmpty() && relsB.isEmpty()) {
			// Fall back to comparing axiom id. We can't return 0 otherwise one axiom will be deleted in a sorted set.
			return a.getAxiomId().compareTo(b.getAxiomId());
		} else if (relsA.isEmpty()) {
			return -1;
		} else if (relsB.isEmpty()) {
			return 1;
		} else {
			Relationship[] arrayA = relsA.toArray(new Relationship[]{});
			Relationship[] arrayB = relsB.toArray(new Relationship[]{});
			for (int i = 0; i < arrayA.length; i++) {
				Relationship relationshipA = arrayA[i];
				if (arrayB.length == i) {
					return 1;
				}
				Relationship relationshipB = arrayB[i];
				int compare = RELATIONSHIP_COMPARATOR.compare(relationshipA, relationshipB);
				if (compare != 0) {
					return compare;
				}
			}
		}
		// Fall back to comparing axiom id.
		return a.getAxiomId().compareTo(b.getAxiomId());
	};

	@PostConstruct
	public void init() {
		domainAttributeOrderMap = sortOrderProperties.getDomainAttributeOrderMap();
	}

	void sortAttributes(Iterable<Concept> concepts) {
		for (Concept concept : concepts) {
			// Attributes sort order is specified per hierarchy.
			// Use FSN semantic tag to allow correct attribute sorting on concepts which have not yet been classified.
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
					concept.setClassAxioms(getSortedAxioms(concept.getClassAxioms()));

					for (Axiom axiom : concept.getGciAxioms()) {
						axiom.setRelationships(getSortedRelationships(axiom.getRelationships(), attributeOrderMap, concept.getConceptId()));
					}
					concept.setGciAxioms(getSortedAxioms(concept.getGciAxioms()));

					concept.setRelationships(getSortedRelationships(concept.getRelationships(), attributeOrderMap, concept.getConceptId()));
				}
			}
		}
	}

	private Set<Axiom> getSortedAxioms(Set<Axiom> axioms) {
		TreeSet<Axiom> sortedSet = new TreeSet<>(AXIOM_COMPARATOR);
		sortedSet.addAll(axioms);
		return new LinkedHashSet<>(sortedSet);
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
		TermLangPojo fsn = DescriptionHelper.getFsnDescriptionTermAndLang(concept.getDescriptions(), EN_LANGUAGE_DIALECT);
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
					group++;
					boolean isSelfGrouped = isSeflGroupedAtrributes(groupId, sortedUngroupedRelationships);
					oldGroupToNewGroupMap.put(groupId, isSelfGrouped ? group : group + NEXT_GROUP_DEFAULT);
				}
			}
		}

		Set<Relationship> sortedRelationships = new TreeSet<>(RELATIONSHIP_COMPARATOR);
		for (Relationship relationship : relationships) {
			if (relationship.getGroupId() != 0 && oldGroupToNewGroupMap.containsKey(relationship.getGroupId())) {
				relationship.setGroupOrder(oldGroupToNewGroupMap.get(relationship.getGroupId()));
			}
			sortedRelationships.add(relationship);
		}

		if (sortedRelationships.size() != originalSize) {
			throw new IllegalStateException(String.format("Sorted attribute set is smaller than original set for conceptId %s. Do duplicates exist?", conceptId));
		}

		return new LinkedHashSet<>(sortedRelationships);
	}

    private boolean isSeflGroupedAtrributes(int groupId, Set<Relationship> relationshipSet) {
	    int count = 0;
        for (Relationship relationship : relationshipSet) {
            if (relationship.getGroupId() == groupId) {
                count++;
            }
        }
        return count == 1;
    }

	Map<String, String> getSubHierarchyToTopLevelTagCache() {
		return subHierarchyToTopLevelTagCache;
	}
}
