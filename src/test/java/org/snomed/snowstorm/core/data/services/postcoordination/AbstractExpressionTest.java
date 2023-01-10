package org.snomed.snowstorm.core.data.services.postcoordination;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

import static org.snomed.snowstorm.core.data.domain.Concepts.*;

public abstract class AbstractExpressionTest extends AbstractTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ReferenceSetMemberService memberService;

    @Autowired
    private ImportService importService;

    @Autowired
    private VersionControlHelper versionControlHelper;

    @Autowired
    private BranchService branchService;

    @Autowired
    private MRCMService mrcmService;

    @Autowired
    private CodeSystemService codeSystemService;

    protected ExpressionContext expressionContext;

    protected final String branch = "MAIN/A";
    protected final String moduleId = "11000003104";

    @BeforeEach
    public void setup() throws ServiceException, ReleaseImportException, IOException {
        conceptService.batchCreate(Lists.newArrayList(
                new Concept(Concepts.SNOMEDCT_ROOT),
                new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addFSN("Concept model object attribute").addRelationship(ISA, SNOMEDCT_ROOT),
                new Concept("421720008"),
                new Concept("405815000"),
                new Concept("49062001").addFSN("Device (physical object)"),
                new Concept("122456005").addFSN("Laser device").addRelationship(ISA, "49062001"),
                new Concept("7946007"),
                new Concept("388441000").addFSN("Horse"),

                new Concept("71388002").addFSN("Procedure").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
                new Concept("363704007").addFSN("Procedure site (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("405813007").addFSN("Procedure site - direct (attribute)").addRelationship(ISA, "363704007"),
                new Concept("260686004").addFSN("Method (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("272741003").addFSN("Laterality (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),

                new Concept("129264002").addFSN("Action (qualifier value)").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
                new Concept("129304002").addFSN("Excision - action").addRelationship(ISA, "129264002"),
                new Concept("182353008").addFSN("Side").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("24028007").addFSN("Right").addRelationship(ISA, "182353008"),
                new Concept("7771000").addFSN("Left").addRelationship(ISA, "182353008"),

                new Concept("442083009").addFSN("Anatomical or acquired body structure (body structure)").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
                new Concept("15497006").addFSN("Ovarian structure").addRelationship(ISA, "442083009"),

                // 83152002 |Oophorectomy| :
                //{ 260686004 |Method| = 129304002 |Excision - action|,
                //   405813007 |Procedure site - Direct| = 15497006 |Ovarian structure|,
                new Concept("83152002")
                        .addFSN("Oophorectomy")
                        .addRelationship(ISA, "71388002")
                        .addRelationship(1, "260686004 | method |", "129304002 |Excision - action|")
                        .addRelationship(1, "405813007 |Procedure site - Direct|", "15497006 |Ovarian structure|"),

                new Concept("404684003").addFSN("Clinical finding").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
                new Concept("64572001").addFSN("Disorder").addRelationship(ISA, "404684003"),
                new Concept("42752001").addFSN("Due to (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("246112005").addFSN("Severity (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("272141005").addFSN("Severities").addRelationship(ISA, "64572001"),
                new Concept("24484000").addFSN("Severe").addRelationship(ISA, "272141005"),
                new Concept("195967001").addFSN("Asthma").addRelationship(ISA, "64572001"),
                new Concept("55985003").addFSN("Atopic reaction").addRelationship(ISA, "64572001"),

                new Concept("254837009").addFSN("Breast cancer").addRelationship(ISA, "64572001"),
                new Concept("408731000").addFSN("Temporal context").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("410510008").addFSN("Temporal context value (qualifier value)"),
                new Concept("410513005").addFSN("Past").addRelationship(ISA, "410510008"),
                new Concept("408732007").addFSN("Subject relationship context").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("125676002").addFSN("Person (person)"),
                new Concept("72705000").addFSN("Mother").addRelationship(ISA, "125676002"),
                new Concept("246090004").addFSN("Associated finding").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),

                new Concept(FINDING_SITE).addFSN("Finding site").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("49755003").addFSN("Morphologically abnormal structure (morphologic abnormality)").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
                new Concept("61685007").addFSN("Lower limb structure").addRelationship(ISA, "442083009"),
                new Concept("116676008").addFSN("Associated morphology (attribute)").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("385627004").addFSN("Cellulitis").addRelationship(ISA, "49755003"),
                new Concept("44132006").addFSN("Abscess").addRelationship(ISA, "49755003"),
                new Concept("449702005").addFSN("Cellulitis and abscess of lower limb")
                        .addRelationship(ISA, "64572001")
                        .addRelationship(1, FINDING_SITE, "61685007")
                        .addRelationship(1, "116676008", "385627004")
                        .addRelationship(2, FINDING_SITE, "61685007")
                        .addRelationship(2, "116676008", "44132006"),

                new Concept("129357001").addFSN("Closure - action").addRelationship(ISA, "129264002"),
                new Concept("344001").addFSN("Ankle region structure").addRelationship(ISA, "442083009"),
                new Concept("363700003").addFSN("Direct morphology").addRelationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE),
                new Concept("13924000").addFSN("Wound").addRelationship(ISA, "49755003"),
                new Concept("105590001").addFSN("Substance").addRelationship(ISA, SNOMEDCT_ROOT),
                new Concept("256683004").addFSN("Flap").addRelationship(ISA, "105590001"),


                // === 14600001000004107 |Closure of wound of ankle with flap| :  \n" +
                //						"{ 260686004 |Method|  =  129357001 |Closure - action| ,\n" +
                //						"405813007 |Procedure site - Direct|  = ( 344001 |Ankle region structure| :  272741003 |Laterality| = 7771000 |Left| ),\n" +
                //						"363700003 |Direct morphology|  =  13924000 |Wound| ,\n" +
                //						"424361007 |Using substance|  =  256683004 |Flap| }
                new Concept("14600001000004107").addFSN("Closure of wound of ankle with flap")
                        .addRelationship(ISA, "71388002")
                        .addRelationship(1, "260686004", "129357001")
                        .addRelationship(1, "405813007", "344001")
                        .addRelationship(1, "363700003", "13924000")
                        .addRelationship(1, "424361007", "256683004")


        ), "MAIN");

        memberService.createMembers("MAIN", Sets.newHashSet(
                new ReferenceSetMember(CORE_MODULE, LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET, "61685007"),
                new ReferenceSetMember(CORE_MODULE, LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET, "344001")
        ));

        File dummyMrcmImportFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_dummy_mrcm_snap");
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
        try (FileInputStream inputStream = new FileInputStream(dummyMrcmImportFile)) {
            importService.importArchive(importJob, inputStream);
        }
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-A", "MAIN/A"));
        expressionContext = new ExpressionContext("MAIN/A", branchService, versionControlHelper, mrcmService, new TimerUtil(""));
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        conceptService.deleteAll();
    }
}
