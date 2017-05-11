package org.ihtsdo.elasticsnomed.services.transitiveclosure;

import java.util.HashSet;
import java.util.Set;

public class Node {

	private final Long id;
	private final Set<Node> parents;
	private boolean updated;

	Node(Long id) {
		this.id = id;
		parents = new HashSet<>();
	}

	public Set<Long> getTransitiveClosure() {
		Set<Long> parentIds = new HashSet<>();
		return getTransitiveClosure(parentIds);
	}

	private Set<Long> getTransitiveClosure(Set<Long> parentIds) {
		parents.forEach(node -> {
			parentIds.add(node.getId());
			node.getTransitiveClosure(parentIds);
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

	public boolean isAncestorOrSelfUpdated() {
		return isAncestorOrSelfUpdated(this);
	}

	private boolean isAncestorOrSelfUpdated(Node node) {
		if (node.updated) {
			return true;
		}
		for (Node parent : node.parents) {
			if (parent.isAncestorOrSelfUpdated()) {
				return true;
			}
		}
		return false;
	}

	public void markUpdated() {
		this.updated = true;
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
