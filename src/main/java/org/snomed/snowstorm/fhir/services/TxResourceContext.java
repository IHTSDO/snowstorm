package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Resource;

import java.util.Collections;
import java.util.Map;

/**
 * Holds the per-request tx-resource overlay as a thread-local map keyed by canonical URL.
 * Resources are set at the provider layer before the operation begins and cleared in a
 * finally block when it returns, so they never outlive the request.
 */
public final class TxResourceContext {

	private static final ThreadLocal<Map<String, Resource>> CURRENT = new ThreadLocal<>();

	private TxResourceContext() {}

	public static void set(Map<String, Resource> overlay) {
		CURRENT.set(overlay);
	}

	/** Returns the current overlay, or an empty map if none is set. */
	public static Map<String, Resource> get() {
		Map<String, Resource> overlay = CURRENT.get();
		return overlay != null ? overlay : Collections.emptyMap();
	}

	/**
	 * Looks up a resource by URL and optional version.
	 * <ul>
	 *   <li>When {@code version} is non-null: tries {@code url|version} first, then the plain URL key.</li>
	 *   <li>When {@code version} is null: tries the plain URL key first, then any {@code url|*} versioned entry
	 *       (to handle callers that do not specify a version for a versioned tx-resource).</li>
	 * </ul>
	 */
	public static Resource lookup(String url, String version) {
		Map<String, Resource> overlay = get();
		if (overlay.isEmpty() || url == null) {
			return null;
		}
		if (version != null) {
			Resource r = overlay.get(url + "|" + version);
			if (r != null) {
				return r;
			}
		}
		Resource plain = overlay.get(url);
		if (plain != null || version != null) {
			return plain;
		}
		// No plain-URL entry and no version requested — fall back to any versioned entry for this URL.
		String prefix = url + "|";
		return overlay.entrySet().stream()
				.filter(e -> e.getKey().startsWith(prefix))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);
	}

	public static void clear() {
		CURRENT.remove();
	}
}
