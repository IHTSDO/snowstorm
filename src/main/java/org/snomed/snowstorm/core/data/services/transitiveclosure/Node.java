package org.snomed.snowstorm.core.data.services.transitiveclosure;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class Node {

	private final Long id;
	private final Set<Node> parents;
	private boolean updated;

	private static final Logger LOGGER = LoggerFactory.getLogger(Node.class);

	Node(Long id) {
		this.id = id;
		parents = new HashSet<>();
	}

	public Set<Long> getTransitiveClosure(String path, boolean throwExceptionIfLoopFound) throws GraphBuilderException {
		Set<Long> parentIds = throwExceptionIfLoopFound ? new LinkedHashSet<>() : new LongOpenHashSet();
		getTransitiveClosure(parentIds);
		if (parentIds.contains(id)) {
			String message = String.format("Loop found in transitive closure for concept %s on branch %s. The concept %s is in its own set of ancestors: %s", id, path, id, parentIds);
			if (throwExceptionIfLoopFound) {
				dumpTransitiveClosure();
				throw new GraphBuilderException(message);
			} else {
				LOGGER.warn(message);
			}
			parentIds.remove(id);
		}
		return parentIds;
	}

	private void getTransitiveClosure(Set<Long> parentIds) {
		for (Node parent : parents) {
			if (parentIds.add(parent.getId())) {
				parent.getTransitiveClosure(parentIds);
			}
		}
	}

	public boolean isAncestorOrSelfUpdated() {
		return isAncestorOrSelfUpdated(new LongOpenHashSet());
	}

	private boolean isAncestorOrSelfUpdated(Set<Long> parentIds) {
		if (updated) {
			return true;
		}
		for (Node parent : parents) {
			if (parentIds.add(parent.getId())) {
				if (parent.isAncestorOrSelfUpdated(parentIds)) {
					return true;
				}
			}
		}
		return false;
	}

	void addParent(Node parent) {
		parents.add(parent);
	}

	public Long getId() {
		return id;
	}

	public Node markUpdated() {
		this.updated = true;
		return this;
	}

	public Set<Node> getParents() {
		return parents;
	}

	private void dumpTransitiveClosure() {
		Set<Long> covered = new HashSet<>();
		PrintStream printStream = System.out;
		printStream.println();
		printStream.println("Dumping transitive closure for concept " + id + ", order is BOTTOM UP!");
		doDumpTransitiveClosure(covered, "- ", printStream);
		printStream.println();
	}

	private void doDumpTransitiveClosure(Set<Long> covered, String indent, PrintStream printStream) {
		printStream.print(indent + id);
		if (covered.contains(id)) {
			if (!parents.isEmpty()) {
				printStream.print("(parents already output)");
			}
			printStream.println();
		} else {
			covered.add(id);
			indent = "|" + indent;
			printStream.println();
			for (Node parent : parents) {
				parent.doDumpTransitiveClosure(covered, indent, printStream);
			}
		}
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
