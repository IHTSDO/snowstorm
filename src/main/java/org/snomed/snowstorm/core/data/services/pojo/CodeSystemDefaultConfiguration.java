package org.snomed.snowstorm.core.data.services.pojo;

public record CodeSystemDefaultConfiguration(String name,
                                             String shortName,
                                             String module,
                                             String countryCode,
                                             String owner,
                                             String alternateSchemaUri,
                                             String alternateSchemaSctid) {

}
