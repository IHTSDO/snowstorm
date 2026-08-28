package org.snomed.snowstorm.core.data.services.pojo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonDeserializeAs;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.snomed.snowstorm.core.data.domain.ComponentType;

import java.util.*;

/**
 * History of a Concept.
 */
public class ConceptHistory {
    private static final Comparator<String> STRING_COMPARATOR = Comparator.reverseOrder();
    private static final Comparator<ConceptHistoryItem> CONCEPT_HISTORY_ITEM_COMPARATOR = (o1, o2) -> o2.getEffectiveTime().compareTo(o1.getEffectiveTime());

    private final String conceptId;
    private final List<ConceptHistoryItem> history = new ArrayList<>();

    public ConceptHistory() {
        this.conceptId = null;
    }

    public ConceptHistory(String conceptId) {
        this.conceptId = conceptId;
    }

    /*
    Deserialisation creator. conceptId is final, and history is final plus initialised inline, so
    Jackson 2 populated it through getHistory() via MapperFeature.USE_GETTERS_AS_SETTERS - which
    defaulted to true in Jackson 2 and false in Jackson 3. Without this creator the list silently
    stayed empty. See the matching creator on ConceptHistoryItem.
    */
    @JsonCreator
    public ConceptHistory(@JsonProperty("conceptId") String conceptId,
                          @JsonProperty("history") List<ConceptHistoryItem> history) {
        this.conceptId = conceptId;
        if (history != null) {
            this.history.addAll(history);
        }
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

        @JsonDeserializeAs(TreeSet.class)
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

        /*
        Deserialisation creator, shaped like the serialised form. The fields are final, and Jackson 3
        no longer writes to final fields (MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS defaulted to
        true in Jackson 2, false in Jackson 3), so without this the no-arg constructor was used and
        every item came back with null effectiveTime/branch and no componentTypes. The constructor
        above cannot serve: it takes a single ComponentType, whereas the JSON carries an array.
        componentTypes is final and initialised inline, so it is populated rather than assigned.
        */
        @JsonCreator
        public ConceptHistoryItem(@JsonProperty("effectiveTime") String effectiveTime,
                                  @JsonProperty("branch") String branch,
                                  @JsonProperty("componentTypes") Set<ComponentType> componentTypes) {
            this.effectiveTime = effectiveTime;
            this.branch = branch;
            if (componentTypes != null) {
                this.componentTypes.addAll(componentTypes);
            }
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
