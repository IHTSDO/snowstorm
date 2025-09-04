package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents dependency information for a code system.
 */
public record DependencyInfo(
    @JsonProperty("codeSystem") String codeSystem,
    @JsonProperty("version") String version
) {
}
