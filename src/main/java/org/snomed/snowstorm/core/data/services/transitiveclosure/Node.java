package org.snomed.snowstorm.core.data.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Node {

	private static final int TC_MAX_DEPTH = 500;
	private static final int SAME_ANCESTOR_IN_TC_MAX = 30;

	private final Long id;
	private final Set<Node> parents;
	private boolean updated;

	private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);

	Node(Long id) {
		this.id = id;
		parents = new HashSet<>();
	}

	public Set<Long> getTransitiveClosure(String path, boolean throwExceptionIfLoopFound) throws GraphBuilderException {
		List<Long> parentIds = new LongArrayList();
		boolean success = getTransitiveClosure(parentIds, 1, throwExceptionIfLoopFound);
		if (!success) {
			Set<Long> parentSet = new HashSet<>(parentIds);
			LOGGER.error("Transitive closure depth exceeded, is this a loop? Concept:{}, Path:{}, Ancestor Set so far:{}, Ancestors list in collection order:{}",
					id, path, parentSet, parentIds);
			throw new GraphBuilderException(String.format("Transitive closure depth exceeded in path:'%s'. See log for details.", path));
		}
		// Return a Set to remove duplicates.
		return new HashSet<>(parentIds);
	}

	private boolean getTransitiveClosure(List<Long> parentIds, final int depth, boolean throwExceptionIfLoopFound) {
		if (depth > TC_MAX_DEPTH) {
			return false;
		}
		int nextDepth = depth + 1;
		for (Node parent : parents) {
			Long parentId = parent.getId();
			parentIds.add(parentId);
			if (parentIds.contains(parentId)) {
				// Concepts may have the same grandparent through different parent routes.
				// We should only fail here if the same ancestor has been hit an unacceptable number of times.
				int visits = 0;
				for (Long parentVisited : parentIds) {
					if (parentVisited.equals(parentId)) {
						visits++;
					}
				}
				if (visits >= SAME_ANCESTOR_IN_TC_MAX) {
					if (throwExceptionIfLoopFound) {
						// Exception will be thrown by calling method
						return false;
					} else {
						// Skip this parent, we have already visited it many times.
						continue;
					}
				}
			}
			if (!parent.getTransitiveClosure(parentIds, nextDepth, throwExceptionIfLoopFound)) {
				return false;
			}
		}
		return true;
	}

	public Long getId() {
		return id;
	}

	void addParent(Node parent) {
		parents.add(parent);
	}

	void removeParent(Long parentId) {
		parents.remove(new Node(parentId));
	}

	public boolean isAncestorOrSelfUpdated(String path) {
		return isAncestorOrSelfUpdated(this, 1, path);
	}

	private boolean isAncestorOrSelfUpdated(Node node, int depth, String path) {
		if (depth > 50) {
			String message = "Node updated check has exceeded the soft limit for concept " + id + " on path " + path + ", working around.";
			LOGGER.warn(message);
			// None found before recursion, returning false allows other paths to be explored.
			return false;
		}
		if (node.updated) {
			return true;
		}
		for (Node parent : node.parents) {
			if (isAncestorOrSelfUpdated(parent, depth + 1, path)) {
				return true;
			}
		}
		return false;
	}

	public Node markUpdated() {
		this.updated = true;
		return this;
	}

	public Set<Node> getParents() {
		return parents;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Node node = (Node) o;

		return id.equals(node.id);

	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
