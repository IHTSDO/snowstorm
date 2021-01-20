package org.snomed.snowstorm.core.data.services.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.snomed.snowstorm.core.data.domain.ComponentType;

import java.util.*;

/**
 * History of a Concept.
 */
public class ConceptHistory {
    private static final Comparator<String> STRING_COMPARATOR = Comparator.reverseOrder();
    private static final Comparator<ConceptHistoryItem> CONCEPT_HISTORY_ITEM_COMPARATOR = (o1, o2) -> {
        return o2.getEffectiveTime().compareTo(o1.getEffectiveTime());
    };

    private final String conceptId;
    private final List<ConceptHistoryItem> history = new ArrayList<>();

    public ConceptHistory() {
        this.conceptId = null;
    }

    public ConceptHistory(String conceptId) {
        this.conceptId = conceptId;
    }

    public String getConceptId() {
        return this.conceptId;
    }

    public List<ConceptHistoryItem> getHistory() {
        this.history.sort(CONCEPT_HISTORY_ITEM_COMPARATOR);
        return this.history;
    }

    public void addToHistory(String effectiveTime, String branch, ComponentType componentType) {
        for (ConceptHistoryItem conceptHistoryItem : this.history) {
            if (conceptHistoryItem.getBranch().equals(branch) && conceptHistoryItem.getEffectiveTime().equals(effectiveTime)) {
                conceptHistoryItem.addComponentType(componentType);
                return;
            }
        }

        this.history.add(new ConceptHistoryItem(branch, componentType, effectiveTime));
    }

    @JsonIgnore
    public Optional<ConceptHistoryItem> getConceptHistoryItem(String effectiveTime) {
        for (ConceptHistoryItem conceptHistoryItem : this.history) {
            if (effectiveTime.equals(conceptHistoryItem.getEffectiveTime())) {
                return Optional.of(conceptHistoryItem);
            }
        }

        return Optional.empty();
    }

    @JsonIgnore
    public List<String> getAllEffectiveTimes() {
        List<String> allEffectiveTimes = new ArrayList<>();
        this.history.forEach(historyItem -> allEffectiveTimes.add(historyItem.getEffectiveTime()));
        allEffectiveTimes.sort(STRING_COMPARATOR);

        return allEffectiveTimes;
    }

    public static class ConceptHistoryItem {
        private final String effectiveTime;
        private final String branch;

        @JsonDeserialize(as = TreeSet.class)
        private final Set<ComponentType> componentTypes = new TreeSet<>();

        public ConceptHistoryItem() {
            this.effectiveTime = null;
            this.branch = null;
        }

        public ConceptHistoryItem(String branch, ComponentType componentType, String effectiveTime) {
            this.effectiveTime = effectiveTime;
            this.branch = branch;
            this.componentTypes.add(componentType);
        }

        public String getEffectiveTime() {
            return this.effectiveTime;
        }

        public String getBranch() {
            return this.branch;
        }

        public Set<ComponentType> getComponentTypes() {
            return this.componentTypes;
        }

        public void addComponentType(ComponentType componentType) {
            this.componentTypes.add(componentType);
        }
    }
}
