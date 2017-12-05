package org.snomed.snowstorm.core.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapUtil {

	public static boolean containsAllKeysAndSetsAreSupersets(Map<String, Set<String>> map, Map<String, Set<String>> otherMap) {
		if (otherMap == null) {
			return true;
		} else if (map == null) {
			return otherMap.isEmpty();
		} else {
			if (!map.keySet().containsAll(otherMap.keySet())) {
				return false;
			}
			for (String key : map.keySet()) {
				if (!map.get(key).containsAll(otherMap.get(key))) {
					return false;
				}
			}
		}
		return true;
	}

	public static Map<String, Set<String>> collectMissingKeyValues(Map<String, Set<String>> map, Map<String, Set<String>> otherMap) {
		HashMap<String, Set<String>> missing = new HashMap<>();

		if (otherMap == null) {
			return missing;
		} else if (map == null) {
			missing.putAll(otherMap);
		} else {
			for (String key : otherMap.keySet()) {
				Set<String> values = map.get(key);
				Set<String> otherValues = otherMap.get(key);
				if (values == null) {
					missing.put(key, otherValues);
				} else if (otherValues != null) {
					Set<String> otherValuesMissing = new HashSet<>(otherValues);
					otherValuesMissing.removeAll(values);
					if (!otherValuesMissing.isEmpty()) {
						missing.put(key, otherValuesMissing);
					}
				}
			}
		}

		return missing;
	}

}
