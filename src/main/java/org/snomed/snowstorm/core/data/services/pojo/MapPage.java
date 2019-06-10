package org.snomed.snowstorm.core.data.services.pojo;

import org.springframework.data.domain.Pageable;

import java.util.Map;

public class MapPage<K, V> {

	private final Map<K, V> map;
	private final Pageable pageable;
	private final long totalElements;

	public MapPage(Map<K, V> map, Pageable pageable, long totalElements) {
		this.map = map;
		this.pageable = pageable;
		this.totalElements = totalElements;
	}

	public Map<K, V> getMap() {
		return map;
	}

	public Pageable getPageable() {
		return pageable;
	}

	public long getTotalElements() {
		return totalElements;
	}
}
