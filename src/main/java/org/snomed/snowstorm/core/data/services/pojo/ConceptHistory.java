package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.data.domain.ComponentType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * History of a Concept.
 */
public class ConceptHistory {
    private final String conceptId;
    private final Map<String, Set<ComponentType>> history = new HashMap<>();

    public ConceptHistory() {
        this.conceptId = null;
    }

    public ConceptHistory(String conceptId) {
        this.conceptId = conceptId;
    }

    public String getConceptId() {
        return this.conceptId;
    }

    public Map<String, Set<ComponentType>> getHistory() {
        return this.history;
    }

    public void addToHistory(String effectiveTime, ComponentType componentType) {
        Set<ComponentType> componentTypes = this.history.get(effectiveTime);
        if (componentTypes != null) {
            componentTypes.add(componentType);
        } else {
            componentTypes = new HashSet<>();
            componentTypes.add(componentType);
            this.history.put(effectiveTime, componentTypes);
        }
    }
}
