package org.snomed.snowstorm.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Type;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.apache.http.client.utils.URLEncodedUtils.format;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class DescriptionControllerTest extends AbstractTest {

    private static final String EXISTING_CONCEPT_ID = "84101006";

    private static final String NONEXISTENT_CONCEPT_ID = "nonexistentConceptId";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConceptService conceptService;

    @BeforeEach
    void setup() throws ServiceException {
        Concept concept = conceptService.create(
                new Concept(EXISTING_CONCEPT_ID)
                        .addDescription(new Description("Human coronavirus (organism)")
                                .setTypeId(Concepts.FSN)
                                .addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
                        .addDescription(new Description("Human coronavirus")
                                .setTypeId(Concepts.SYNONYM)
                                .addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
                , MAIN);
        conceptService.update(concept, MAIN);
    }

    @Test
    void testFindDescriptionsOfEmptyConceptId() throws JSONException {
        Set<Description> descriptions = getDescriptionsBySingleConceptIdScenario(EXISTING_CONCEPT_ID);

        Page<Concept> concepts = conceptService.findAll(MAIN, LARGE_PAGE);
        Set<Description> allDescriptions = concepts.stream().flatMap(concept -> concept.getDescriptions().stream()).collect(toSet());
        assertThat(descriptions).containsAll(allDescriptions);
    }

    @Test
    void testFindDescriptionsOfNonExistentConceptId() throws JSONException {
        Set<Description> descriptions = getDescriptionsBySingleConceptIdScenario(NONEXISTENT_CONCEPT_ID);

        assertThat(descriptions).isEmpty();
    }

    @Test
    void testFindDescriptionsOfNoConceptId() throws JSONException {
        Set<Description> descriptions = getDescriptionsBySingleConceptIdScenario("");

        assertAllDescriptions(descriptions);
    }

    @Test
    void testFindDescriptionsOfEmptyConceptIds() throws JSONException {
        Set<String> conceptIds = emptySet();

        Set<Description> descriptions = getDescriptionsByMultipleConceptIdsScenario(conceptIds);

        assertAllDescriptions(descriptions);
    }

    @Test
    void testFindDescriptionsOfSomeExistingConceptIds() throws JSONException {
        Set<String> conceptIds = newHashSet(EXISTING_CONCEPT_ID);

        Set<Description> descriptions = getDescriptionsByMultipleConceptIdsScenario(conceptIds);

        assertThat(descriptions).extracting("conceptId").contains(EXISTING_CONCEPT_ID);
        assertThat(descriptions).hasSize(2);
    }

    @Test
    void testFindDescriptionsOfSomeExistingAndNonexistentConceptIds() throws JSONException {
        Set<String> conceptIds = newHashSet(EXISTING_CONCEPT_ID, NONEXISTENT_CONCEPT_ID);

        Set<Description> descriptions = getDescriptionsByMultipleConceptIdsScenario(conceptIds);

        assertThat(descriptions).extracting("conceptId").contains(EXISTING_CONCEPT_ID);
        assertThat(descriptions).extracting("conceptId").doesNotContain(NONEXISTENT_CONCEPT_ID);
        assertThat(descriptions).hasSize(2);
    }

    @Test
    void testFindDescriptionsOfSomeNonexistentConceptIds() throws JSONException {
        Set<String> nonexistentConceptIds = newHashSet(NONEXISTENT_CONCEPT_ID);

        Set<Description> descriptions = getDescriptionsByMultipleConceptIdsScenario(nonexistentConceptIds);

        assertThat(descriptions).isEmpty();
    }

    private Set<Description> getDescriptionsBySingleConceptIdScenario(String conceptId) throws JSONException {
        String url = "http://localhost:" + port + "/MAIN/descriptions?conceptId=" + conceptId;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        JSONObject jsonObject = new JSONObject(response.getBody());
        Type type = new TypeToken<Set<Description>>() {}.getType();
        return new Gson().fromJson(jsonObject.get("items").toString(), type);
    }

    private Set<Description> getDescriptionsByMultipleConceptIdsScenario(Set<String> conceptIds) throws JSONException {
        Set<BasicNameValuePair> conceptIdsQueryString = conceptIds.stream()
                .map(conceptId -> new BasicNameValuePair("conceptIds", conceptId))
                .collect(toSet());
        String url = "http://localhost:" + port + "/MAIN/descriptions?" + format(conceptIdsQueryString, UTF_8);
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        JSONObject jsonObject = new JSONObject(response.getBody());
        Type type = new TypeToken<Set<Description>>() {}.getType();
        return new Gson().fromJson(jsonObject.get("items").toString(), type);
    }

    private void assertAllDescriptions(Set<Description> descriptions) {
        Page<Concept> concepts = conceptService.findAll(MAIN, LARGE_PAGE);
        Set<Description> allDescriptions = concepts.stream().flatMap(concept -> concept.getDescriptions().stream()).collect(toSet());
        assertThat(descriptions).containsAll(allDescriptions);
    }
}
