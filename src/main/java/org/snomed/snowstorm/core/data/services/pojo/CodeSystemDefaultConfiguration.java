package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.data.domain.PackageType;

public record CodeSystemDefaultConfiguration(String name,
                                             String shortName,
                                             String module,
                                             String countryCode,
                                             String owner,
                                             String alternateSchemaUri,
                                             String alternateSchemaSctid,
                                             PackageType packageType) {

}
