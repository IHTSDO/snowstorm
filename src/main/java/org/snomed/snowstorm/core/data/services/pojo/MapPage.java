package org.snomed.snowstorm.core.data.services.pojo;

import org.springframework.data.domain.Pageable;

import java.util.Map;

public record MapPage<K, V>(Map<K, V> map, Pageable pageable, long totalElements) {
}
