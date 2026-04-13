package org.snomed.snowstorm.core.data.services;

import org.apache.commons.lang3.StringUtils;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.rest.pojo.PartialHierarchyNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PartialHierarchyService {

	private final ConceptService conceptService;
	private final SemanticIndexService semanticIndexService;

	public PartialHierarchyService(ConceptService conceptService, SemanticIndexService semanticIndexService) {
		this.conceptService = conceptService;
		this.semanticIndexService = semanticIndexService;
	}

	/**
	 * Load inferred IS-A subgraph for the seed codes, optionally join preferred terms, and return nodes ordered by
	 * layer then display term (when includeTerms) or concept id.
	 */
	public List<PartialHierarchyNode> loadPartialHierarchy(String branchPath, List<String> codes, boolean includeTerms,
			List<LanguageDialect> languageDialects) {

		List<Long> seedIds = parseConceptIds(codes);
		Map<Long, QueryConcept> subgraph = semanticIndexService.loadQueryConceptSubgraph(branchPath, seedIds, false);
		List<QueryConcept> ordered = new ArrayList<>(subgraph.values());
		final Map<String, ConceptMini> miniMap;
		if (includeTerms && !ordered.isEmpty()) {
			miniMap = conceptService.findConceptMinis(branchPath,
					ordered.stream().map(QueryConcept::getConceptIdL).toList(),
					languageDialects).getResultsMap();
			Map<Long, Integer> layerDepths = computeLayerDepths(subgraph);
			Comparator<String> termOrder = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
			ordered.sort(Comparator
					.comparing((QueryConcept qc) -> layerDepths.getOrDefault(qc.getConceptIdL(), 0))
					.thenComparing(qc -> preferredDisplayTerm(miniMap.get(qc.getConceptIdL().toString())), termOrder)
					.thenComparing(QueryConcept::getConceptIdL));
		} else {
			miniMap = Collections.emptyMap();
			ordered.sort(Comparator.comparing(QueryConcept::getConceptIdL));
		}
		List<PartialHierarchyNode> out = new ArrayList<>(ordered.size());
		for (QueryConcept qc : ordered) {
			String[] parents = toSortedParentCodeStrings(qc.getParents());
			String term = null;
			if (includeTerms) {
				term = preferredDisplayTerm(miniMap.get(qc.getConceptIdL().toString()));
			}
			out.add(new PartialHierarchyNode(qc.getConceptIdL().toString(), parents, term));
		}
		return out;
	}

	private static List<Long> parseConceptIds(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return List.of();
		}
		List<Long> out = new ArrayList<>(codes.size());
		for (String code : codes) {
			try {
				out.add(Long.parseLong(code));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid concept id: " + code);
			}
		}
		return out;
	}

	private static String[] toSortedParentCodeStrings(Set<Long> parents) {
		if (parents == null || parents.isEmpty()) {
			return new String[0];
		}
		return parents.stream().sorted().map(String::valueOf).toArray(String[]::new);
	}

	private static String preferredDisplayTerm(ConceptMini mini) {
		if (mini == null) {
			return null;
		}
		TermLangPojo pt = mini.getPt();
		if (pt != null && StringUtils.isNotEmpty(pt.getTerm())) {
			return pt.getTerm();
		}
		String fsn = mini.getFsnTerm();
		return StringUtils.isNotEmpty(fsn) ? fsn : null;
	}

	/**
	 * Layer index for each concept: longest path from a root (no in-subgraph parent) within the subgraph,
	 * so parents always appear in an earlier level than their children.
	 */
	private static Map<Long, Integer> computeLayerDepths(Map<Long, QueryConcept> subgraph) {
		Map<Long, Integer> memo = new HashMap<>();
		for (Long id : subgraph.keySet()) {
			layerDepthFromRoots(subgraph, id, memo, new HashSet<>());
		}
		return memo;
	}

	private static int layerDepthFromRoots(Map<Long, QueryConcept> subgraph, Long id, Map<Long, Integer> memo, Set<Long> stack) {
		Integer cached = memo.get(id);
		if (cached != null) {
			return cached;
		}
		if (stack.contains(id)) {
			return 0;
		}
		stack.add(id);
		int d = 0;
		QueryConcept qc = subgraph.get(id);
		if (qc != null && qc.getParents() != null) {
			for (Long p : qc.getParents()) {
				if (subgraph.containsKey(p)) {
					d = Math.max(d, layerDepthFromRoots(subgraph, p, memo, stack) + 1);
				}
			}
		}
		stack.remove(id);
		memo.put(id, d);
		return d;
	}
}
