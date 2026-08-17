package org.snomed.snowstorm.rest.pojo;

import java.util.List;

public record InactivationReason(String id, String name, String displayLabel, List<ValidAssociation> validAssociations) {
}