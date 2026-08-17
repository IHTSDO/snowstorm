package org.snomed.snowstorm.rest.pojo;

public record ValidAssociation(String type, int minTargets, int maxTargets, boolean targetsMustBeActive) {
}