package org.snomed.snowstorm.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.apache.http.client.utils.URLEncodedUtils.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.SNOMEDCT;

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

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private BranchService branchService;

    @BeforeEach
    void setup() throws ServiceException {
        Concept concept = conceptService.create(
                new Concept(EXISTING_CONCEPT_ID)
                        .addDescription(new Description("Human coronavirus (organism)")
                                .setTypeId(FSN)
                                .addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
                        .addDescription(new Description("Human coronavirus")
                                .setTypeId(SYNONYM)
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

    @Test
    void testFindWithApostropheWhenEncounteringElisions() throws ServiceException, JSONException {
        Concept concept;
        String intMain = "MAIN";
        String extMain = "MAIN/SNOMEDCT-XX";
        Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
        CodeSystem codeSystem;

        // Create International
        CodeSystem rootCS = new CodeSystem(SNOMEDCT, Branch.MAIN);
        codeSystemService.createCodeSystem(rootCS);

        // Add Concepts
        concept = new Concept()
                .addDescription(new Description("Intervention (intervention)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Intervention").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
                .addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
        concept = conceptService.create(concept, intMain);
        String interventionId = concept.getConceptId();

        concept = new Concept()
                .addDescription(new Description("Intervention a (intervention)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Intervention a").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, interventionId))
                .addRelationship(new Relationship(ISA, interventionId));
        concept = conceptService.create(concept, intMain);
        String interventionA = concept.getConceptId();

        concept = new Concept()
                .addDescription(new Description("Intervention b (intervention)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Intervention b").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, interventionId))
                .addRelationship(new Relationship(ISA, interventionId));
        concept = conceptService.create(concept, intMain);
        String interventionB = concept.getConceptId();

        concept = new Concept()
                .addDescription(new Description("Intervention c (intervention)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Intervention c").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, interventionId))
                .addRelationship(new Relationship(ISA, interventionId));
        concept = conceptService.create(concept, intMain);
        String interventionC = concept.getConceptId();

        // Version International
        codeSystem = codeSystemService.find("SNOMEDCT");
        codeSystemService.createVersion(codeSystem, 20260101, "20260101");

        // Create Extension
        codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
        concept = conceptService.create(
                new Concept()
                        .addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                        .addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                        .addAxiom(new Relationship(ISA, MODULE)),
                extMain
        );
        String extModuleId = concept.getConceptId();
        branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId));

        // Add language reference set
        concept = new Concept()
                .addDescription(new Description("French language reference set (reference set)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("French language reference set").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, LANG_REFSET))
                .addRelationship(new Relationship(ISA, LANG_REFSET));
        concept = conceptService.create(concept, extMain);
        String frenchReferenceSetId = concept.getConceptId();
        Map<String, String> frAcceptable = Map.of(frenchReferenceSetId, descriptionAcceptabilityNames.get(ACCEPTABLE));

        // Add translation
        concept = conceptService.find(interventionC, extMain);
        concept.addDescription(new Description("l'intervention").setLanguageCode("fr").setTypeId(SYNONYM).setAcceptabilityMap(frAcceptable));
        concept = conceptService.update(concept, extMain);

        // Version extension
        codeSystem = codeSystemService.find("SNOMEDCT-XX");
        codeSystemService.createVersion(codeSystem, 20260102, "20260102");

        // Assert
        Set<Description> extFR = getDescriptionsByText(extMain, "l'intervention");
        Set<Description> extEN = getDescriptionsByText(extMain, "intervention");
        assertEquals(extFR, extEN);

        Set<Description> intFR = getDescriptionsByText(intMain, "l'intervention");
        Set<Description> intEN = getDescriptionsByText(intMain, "intervention");
        assertEquals(intFR, intEN);

        assertTrue(extEN.containsAll(intEN));
        assertFalse(intFR.stream().anyMatch(d -> "l'intervention".equals(d.getTerm())), "French translation should not appear on the international branch");
    }

    @Test
    void testFindWithApostrophe() throws ServiceException, JSONException {
        Concept concept;
        String intMain = "MAIN";
        String extMain = "MAIN/SNOMEDCT-XX";
        Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
        CodeSystem codeSystem;

        // Create International
        CodeSystem rootCS = new CodeSystem(SNOMEDCT, Branch.MAIN);
        codeSystemService.createCodeSystem(rootCS);

        // Add Concepts
        concept = new Concept()
                .addDescription(new Description("Patient's details (details)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Patient's details").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
                .addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
        concept = conceptService.create(concept, intMain);
        String patientsDetails = concept.getConceptId();

        // Version International
        codeSystem = codeSystemService.find("SNOMEDCT");
        codeSystemService.createVersion(codeSystem, 20260101, "20260101");

        // Create Extension
        codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
        concept = conceptService.create(
                new Concept()
                        .addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                        .addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                        .addAxiom(new Relationship(ISA, MODULE)),
                extMain
        );
        String extModuleId = concept.getConceptId();
        branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId));

        // Add language reference set
        concept = new Concept()
                .addDescription(new Description("French language reference set (reference set)").setTypeId(FSN).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("French language reference set").setTypeId(SYNONYM).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, LANG_REFSET))
                .addRelationship(new Relationship(ISA, LANG_REFSET));
        concept = conceptService.create(concept, extMain);
        String frenchReferenceSetId = concept.getConceptId();
        Map<String, String> frAcceptable = Map.of(frenchReferenceSetId, descriptionAcceptabilityNames.get(ACCEPTABLE));

        // Add translation
        concept = conceptService.find(patientsDetails, extMain);
        concept.addDescription(new Description("Les détails du patient").setLanguageCode("fr").setTypeId(SYNONYM).setAcceptabilityMap(frAcceptable));
        concept = conceptService.update(concept, extMain);

        // Version extension
        codeSystem = codeSystemService.find("SNOMEDCT-XX");
        codeSystemService.createVersion(codeSystem, 20260102, "20260102");

        // Assert
        Set<Description> intA = getDescriptionsByText(intMain, "Patient's details");
        Set<Description> intB = getDescriptionsByText(intMain, "patient details");
        assertEquals(2, intB.size());
        assertEquals(2, intA.size());

        Set<Description> extA = getDescriptionsByText(extMain, "Patient's details");
        Set<Description> extB = getDescriptionsByText(extMain, "patient details");
        assertEquals(2, extA.size());
        assertEquals(3, extB.size());
    }

    private Set<Description> getDescriptionsByText(String branchPath, String text) throws JSONException {
        String url = "http://localhost:" + port + "/browser/" + branchPath + "/descriptions?term=" + text;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        JSONObject jsonObject = new JSONObject(response.getBody());
        Type type = new TypeToken<Set<Description>>() {
        }.getType();
        return new Gson().fromJson(jsonObject.get("items").toString(), type);
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
