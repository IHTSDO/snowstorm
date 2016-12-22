package org.ihtsdo.elasticsnomed.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class GraphBuilder {

	private Long2ObjectMap<Node> nodeLookup = new Long2ObjectOpenHashMap<>();

	public void addEdge(Long sourceId, Long destinationId) {
		getCreateNode(sourceId).addEdge(getCreateNode(destinationId));
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
}
