package org.snomed.snowstorm.core.data.services.pojo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.domain.Concepts;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConceptHistoryTest {
    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

    private static void assertIndexIsValue(List<String> effectiveTimes, String expectedEffectiveTime, int index) {
        assertEquals(expectedEffectiveTime, effectiveTimes.get(index));
    }

    @Test
    void conceptHistory_ShouldDeserializeAsExpected() throws JacksonException {
        //given
        final ConceptHistory conceptHistory = new ConceptHistory(Concepts.ISA);
        conceptHistory.addToHistory("20200131", "MAIN", ComponentType.Concept);
        conceptHistory.addToHistory("20210131", "MAIN", ComponentType.Relationship);

        //when
        String result = objectMapper.writeValueAsString(conceptHistory);
        String expectedResult = "{\"conceptId\":\"116680003\",\"history\":[{\"effectiveTime\":\"20210131\",\"branch\":\"MAIN\",\"componentTypes\":[\"Relationship\"]},{\"effectiveTime\":\"20200131\",\"branch\":\"MAIN\",\"componentTypes\":[\"Concept\"]}]}";

        //then
        assertEquals(expectedResult, result);
    }

    @Test
    void getHistory_ShouldReturnElementsInDescendingOrder() {
        //given
        ConceptHistory conceptHistory = new ConceptHistory("12345678910");
        String[] effectiveDates = {
                "20200131", "20180731", "20200731", "20180131",
                "20160731", "20140731", "20160131", "20140131",
                "20120131", "20100131", "20120731", "20100731"
        };
        for (String effectiveDate : effectiveDates) {
            conceptHistory.addToHistory(effectiveDate, "MAIN", ComponentType.Concept);
        }

        //when
        List<String> effectiveTimes = conceptHistory.getAllEffectiveTimes();

        //then
        assertIndexIsValue(effectiveTimes, "20200731", 0);
        assertIndexIsValue(effectiveTimes, "20200131", 1);
        assertIndexIsValue(effectiveTimes, "20180731", 2);
        assertIndexIsValue(effectiveTimes, "20180131", 3);
        assertIndexIsValue(effectiveTimes, "20160731", 4);
        assertIndexIsValue(effectiveTimes, "20160131", 5);
        assertIndexIsValue(effectiveTimes, "20140731", 6);
        assertIndexIsValue(effectiveTimes, "20140131", 7);
        assertIndexIsValue(effectiveTimes, "20120731", 8);
        assertIndexIsValue(effectiveTimes, "20120131", 9);
        assertIndexIsValue(effectiveTimes, "20100731", 10);
        assertIndexIsValue(effectiveTimes, "20100131", 11);
    }
}
