package org.snomed.snowstorm.core.data.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class GraphBuilder {

	private final Map<Long, Node> nodeLookup = new Long2ObjectOpenHashMap<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(GraphBuilder.class);

	public void addParent(Long sourceId, Long destinationId) {
		LOGGER.debug("{} -> {}", sourceId, destinationId);
		Node createNode = getCreateNode(sourceId);
		createNode.addParent(getCreateNode(destinationId));
	}

	private Node getCreateNode(Long id) {
		Node node = nodeLookup.get(id);
		if (node == null) {
			node = new Node(id);
			nodeLookup.put(node.getId(), node);
		}
		return node;
	}

	public Collection<Node> getNodes() {
		return nodeLookup.values();
	}

	public int getNodeCount() {
		return nodeLookup.size();
	}

	public void clearParentsAndMarkUpdated(Long sourceId) {
		getCreateNode(sourceId).markUpdated().getParents().clear();
	}
}
