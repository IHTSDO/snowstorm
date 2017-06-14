package org.ihtsdo.elasticsnomed.core.util;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class MapUtilTest {

	@Test
	public void collectMissingKeyValues() throws Exception {
		assertTrue(MapUtil.collectMissingKeyValues(null, null).isEmpty());
		assertTrue(MapUtil.collectMissingKeyValues(new HashMap<>(), null).isEmpty());
		assertTrue(MapUtil.collectMissingKeyValues(new HashMap<>(), new HashMap<>()).isEmpty());
		assertTrue(MapUtil.collectMissingKeyValues(null, new HashMap<>()).isEmpty());

		HashMap<String, Set<String>> map = new HashMap<>();
		map.put("1", Sets.newHashSet("a"));
		HashMap<String, Set<String>> otherMap = new HashMap<>();
		assertTrue(MapUtil.collectMissingKeyValues(map, otherMap).isEmpty());

		otherMap.put("1", Sets.newHashSet("a"));
		assertTrue(MapUtil.collectMissingKeyValues(map, otherMap).isEmpty());

		map.put("2", Sets.newHashSet("a"));
		assertTrue(MapUtil.collectMissingKeyValues(map, otherMap).isEmpty());

		otherMap.put("2", Sets.newHashSet("a", "b"));
		Map<String, Set<String>> missing = MapUtil.collectMissingKeyValues(map, otherMap);
		assertEquals(1, missing.size());
		assertEquals(Sets.newHashSet("2"), missing.keySet());
		assertEquals(Sets.newHashSet("b"), missing.get("2"));

		otherMap.put("3", Sets.newHashSet("z"));

		missing = MapUtil.collectMissingKeyValues(map, otherMap);
		assertEquals(2, missing.size());
		assertEquals(Sets.newHashSet("2", "3"), missing.keySet());
		assertEquals(Sets.newHashSet("z"), missing.get("3"));
	}

}
