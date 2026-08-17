package org.snomed.snowstorm.rest.pojo;

import java.util.List;

public record InactivationReasonsResponse(List<InactivationReason> conceptReasons, List<InactivationReason> descriptionReasons) {
}