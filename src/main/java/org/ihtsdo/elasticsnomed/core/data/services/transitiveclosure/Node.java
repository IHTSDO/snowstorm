package org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class Node {

	private final Long id;
	private final Set<Node> parents;
	private boolean updated;

	public static final Logger LOGGER = LoggerFactory.getLogger(Node.class);

	Node(Long id) {
		this.id = id;
		parents = new HashSet<>();
	}

	public Set<Long> getTransitiveClosure(String path) {
		Set<Long> parentIds = new HashSet<>();
		return getTransitiveClosure(parentIds, 1, path);
	}

	private Set<Long> getTransitiveClosure(Set<Long> parentIds, final int depth, String path) {
		if (depth > 50) {
			String message = "Transitive closure stack depth has exceeded the soft limit for concept " + id + " on path " + path + ", ancestor ids: " + parentIds.toString();
			LOGGER.error(message);
			return parentIds;
//			throw new RuntimeException(message);
		}
		parents.forEach(node -> {
			parentIds.add(node.getId());
			node.getTransitiveClosure(parentIds, depth + 1, path);
		});
		return parentIds;
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

	public void markUpdated() {
		this.updated = true;
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
