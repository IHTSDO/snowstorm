package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class CodeSystemControllerTest extends AbstractTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private BranchService branchService;

    @Test
    public void listCodeSystems_ShouldReturnCodeSystem_WhenCodeSystemExists() {
        //given
        String branchPath = "MAIN/ProjectA";
        String shortName = "SNOMEDCT-PROJECT-A";
        String requestUrl = listCodeSystems(branchPath);
        givenCodeSystemExists(shortName, branchPath);
        givenCodeSystemVersionExists(shortName, 20200731, "2020 July.");

        //when
        ItemsPage<?> result = this.restTemplate.getForObject(requestUrl, ItemsPage.class);

        //then
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    public void listCodeSystems_ShouldShowLatestVersionDependsOnPreviousVersion() {
        //given
        String mainShortName = "MAIN";
        String projectPath = "MAIN/ProjectA";
        String projectTaskPath = "MAIN/ProjectA/TaskA";
        String projectShortName = "SNOMEDCT-TEST-SHORTNAME";
        String requestUrl = listCodeSystems(projectTaskPath);

        givenBranchExists(projectPath);
        givenBranchExists(projectTaskPath);

        givenCodeSystemExists(mainShortName, mainShortName);
        givenCodeSystemExists(projectShortName, projectPath);

        givenCodeSystemVersionExists(mainShortName, 20200131, "2020 January.");
        givenCodeSystemVersionExists(mainShortName, 20210131, "2021 January.");
        givenCodeSystemVersionExists(projectShortName, 20190731, "2019 July.");
        givenCodeSystemVersionExists(projectShortName, 20200131, "2020 January.");
        givenCodeSystemVersionExists(projectShortName, 20200731, "2020 July.");

        //when
        ItemsPage<ItemsPage<?>> result = this.restTemplate.getForObject(requestUrl, ItemsPage.class);
        Integer dependantVersionEffectiveTime = getDependantVersionEffectiveTime(result);

        //then
        assertEquals(1, result.getTotal());
        assertEquals(20200131, dependantVersionEffectiveTime);
    }

    private String listCodeSystems(String branchPath) {
        return "http://localhost:" + port + "/codesystems/?forBranch=" + branchPath;
    }

    private Integer getDependantVersionEffectiveTime(ItemsPage<ItemsPage<?>> result) {
        Collection<ItemsPage<?>> items = result.getItems();

        if (items instanceof ArrayList) {
            List<LinkedHashMap<?, ?>> itemsList = (ArrayList) items;
            LinkedHashMap<?, ?> linkedHashMap = itemsList.get(0);
            LinkedHashMap<?, ?> latestVersion = (LinkedHashMap) linkedHashMap.get("latestVersion");
            return (Integer) latestVersion.get("dependantVersionEffectiveTime");
        }

        return null;
    }

    private void givenCodeSystemExists(String shortName, String branchPath) {
        codeSystemService.createCodeSystem(new CodeSystem(shortName, branchPath));
    }

    private void givenCodeSystemVersionExists(String shortName, int effectiveDate, String description) {
        codeSystemService.createVersion(codeSystemService.find(shortName), effectiveDate, description);
    }

    private void givenBranchExists(String branchPath) {
        branchService.create(branchPath);
    }
}
