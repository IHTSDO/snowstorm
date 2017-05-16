package org.ihtsdo.elasticsnomed.core.data.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphBuilder {

	private Long2ObjectMap<Node> nodeLookup = new Long2ObjectOpenHashMap<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(GraphBuilder.class);

	public Node addParent(Long sourceId, Long destinationId) {
		LOGGER.debug("{} -> {}", sourceId, destinationId);
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

	public Node removeParent(Long sourceId, Long destinationId) {
		LOGGER.debug("{} X> {}", sourceId, destinationId);
		final Node node = nodeLookup.get(sourceId);
		if (node != null) {
			node.removeParent(destinationId);
		}
		return node;
	}
}
