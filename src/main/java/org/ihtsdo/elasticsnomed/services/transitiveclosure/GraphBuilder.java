package org.ihtsdo.elasticsnomed.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class GraphBuilder {

	private Long2ObjectMap<Node> nodeLookup = new Long2ObjectOpenHashMap<>();

	public Node addParent(Long sourceId, Long destinationId) {
		Node createNode = getCreateNode(sourceId);
		createNode.addParent(getCreateNode(destinationId));
		return createNode;
	}

	private Node getCreateNode(Long id) {
		Node node = nodeLookup.get(id);
		if (node == null) {
			node = new Node(id);
			nodeLookup.put(node.getId(), node);
		}
		return node;
	}

	public Iterable<Node> getNodes() {
		return nodeLookup.values();
	}

	public int getNodeCount() {
		return nodeLookup.size();
	}

	public Node removeParent(long source, long destination) {
		final Node node = nodeLookup.get(source);
		if (node != null) {
			node.removeParent(destination);
		}
		return node;
	}
}
