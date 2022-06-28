package org.snomed.snowstorm.fhir.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class FHIRGraphBuilder {

	private final Map<String, Node> nodeLookup = new HashMap<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(FHIRGraphBuilder.class);

	public void addParent(String sourceCode, String destinationCode) {
		LOGGER.debug("{} -> {}", sourceCode, destinationCode);
		Node createNode = getCreateNode(sourceCode);
		createNode.addParent(getCreateNode(destinationCode));
	}

	private Node getCreateNode(String code) {
		Node node = nodeLookup.get(code);
		if (node == null) {
			node = new Node(code);
			nodeLookup.put(node.getCode(), node);
		}
		return node;
	}

	public Set<String> getTransitiveClosure(String code) {
		Node node = nodeLookup.get(code);
		return node != null ? node.getTransitiveClosure() : null;
	}

	public Collection<String> getNodeParents(String code) {
		Node node = nodeLookup.get(code);
		return node != null ? node.getParents().stream().map(Node::getCode).collect(Collectors.toList()) : Collections.emptyList();
	}

	public Collection<String> getNodeChildren(String code) {
		Node node = nodeLookup.get(code);
		return node != null ? node.getChildren().stream().map(Node::getCode).collect(Collectors.toList()) : Collections.emptyList();
	}

	public static class Node {

		private final String code;
		private final Set<Node> parents;
		private final Set<Node> children;

		public Node(String code) {
			this.code = code;
			parents = new HashSet<>();
			children = new HashSet<>();
		}

		public void addParent(Node parentCode) {
			parents.add(parentCode);
			parentCode.addChild(this);
		}

		private void addChild(Node node) {
			children.add(node);
		}

		public String getCode() {
			return code;
		}

		public Set<String> getTransitiveClosure() {
			HashSet<String> tc = new HashSet<>();
			collectParents(tc);
			return tc;
		}

		private void collectParents(Set<String> tc) {
			for (Node parent : parents) {
				tc.add(parent.getCode());
				parent.collectParents(tc);
			}
		}

		public Set<Node> getParents() {
			return parents;
		}

		public Set<Node> getChildren() {
			return children;
		}
	}
}
