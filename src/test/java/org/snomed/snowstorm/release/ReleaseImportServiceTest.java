package org.snomed.snowstorm.release;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ReleaseImportServiceTest extends AbstractTest {

    @Autowired
    private ReleaseImportService releaseImportService;

    @Autowired
    private ImportService importService;

    @Autowired
    private CodeSystemService codeSystemService;

    @BeforeEach
    void setUp() throws Exception {
        File baseLineRelease = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
        CodeSystem snomedct = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(snomedct);
        String importId = importService.createJob(RF2Type.SNAPSHOT, snomedct.getBranchPath(), true, false);
        importService.importArchive(importId, new FileInputStream(baseLineRelease));
    }

    @Test
    void testPerformScheduledImport() {
        CodeSystem cs = codeSystemService.find("SNOMEDCT");
        Assertions.assertNotNull(cs);
        Assertions.assertEquals("SNOMEDCT", cs.getShortName());
        releaseImportService.performScheduledImport(cs);
        List<CodeSystemVersion> codeSystemVersions = this.codeSystemService.findAllVersions(cs.getShortName(), true, true);
        Assertions.assertEquals(2, codeSystemVersions.size());
        Assertions.assertEquals("2018-07-31", codeSystemVersions.get(0).getVersion());
        Assertions.assertEquals("2025-07-01", codeSystemVersions.get(1).getVersion());
    }
}
