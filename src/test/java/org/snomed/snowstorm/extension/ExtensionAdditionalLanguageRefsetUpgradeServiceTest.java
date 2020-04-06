package org.snomed.snowstorm.extension;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.snomed.snowstorm.core.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExtensionAdditionalLanguageRefsetUpgradeServiceTest extends AbstractTest {

	@Autowired
	private ExtensionAdditionalLanguageRefsetUpgradeService extensionAdditionalLanguageRefsetUpgradeService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionedContentResourceConfig versionedContentResourceConfig;

	@Autowired
	private ResourceLoader resourceLoader;

	private File dependencyPackage;

	private File previousPackage;

	@Before
	public void setUp() throws Exception {
		dependencyPackage = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		previousPackage = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		extensionAdditionalLanguageRefsetUpgradeService.setResourceManager(new MockResourceManager(versionedContentResourceConfig, resourceLoader));
	}

	@After
	public void tearDown() {
		super.defaultTearDown();
		if (dependencyPackage != null && dependencyPackage.exists()) {
			dependencyPackage.delete();
		}
		if (previousPackage != null && previousPackage.exists()) {
			previousPackage.delete();
		}
	}

	@Test
	public void testGenerateAdditionalLanguageRefsets() throws Exception {

		CodeSystem snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		codeSystemService.createVersion(snomedct, 20200131, "2020 January release ");
		CodeSystem snomedctNZ = new CodeSystem("SNOMEDCT-NZ", "MAIN/SNOMEDCT-NZ");
		codeSystemService.createCodeSystem(snomedctNZ);
		Map<String, String> metaData = new HashMap<>();
		metaData.put(BranchMetadataKeys.DEPENDENCY_PACKAGE, dependencyPackage.getAbsolutePath());
		metaData.put(BranchMetadataKeys.PREVIOUS_PACKAGE, previousPackage.getAbsolutePath());
		metaData.put("defaultModuleId", "21000210109");
		metaData.put("defaultNamespace", "1000210");
		metaData.put("shortname", "NZ");
		metaData.put("requiredLanguageRefsets", "[{\n" +
				"        \"default\": \"false\",\n" +
				"        \"en\": \"900000000000508004\",\n" +
				"        \"readOnly\": \"true\",\n" +
				"        \"dialectName\": \"en-gb\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"true\",\n" +
				"        \"en\": \"271000210107\",\n" +
				"        \"dialectName\": \"en-nz\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"false\",\n" +
				"        \"mi\": \"291000210106\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"false\",\n" +
				"        \"en\": \"900000000000509007\",\n" +
				"        \"dialectName\": \"en-us\"\n" +
				"      }\n" +
				"    ]");
		branchService.updateMetadata("MAIN/SNOMEDCT-NZ", metaData);
		File tempResult = Files.createTempFile("tempResult", ".zip").toFile();
		OutputStream rf2DeltaZipResultStream = new FileOutputStream(tempResult);
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, rf2DeltaZipResultStream);

		System.out.println(tempResult.getAbsolutePath());
		// verify results from file
		try (InputStream inputStream  = ZipUtil.getZipEntryStreamOrThrow(tempResult, "der2_cRefset_LanguageDelta-en_NZ1000210_" + DateUtil.getTodaysEffectiveTime() + ".txt");) {
			assertNotNull(inputStream);
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			String line = reader.readLine();
			assertEquals(RF2Constants.LANGUAGE_REFSET_HEADER, line);
			List<String> lines = new ArrayList<>();
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			assertEquals(1, lines.size());
			String[] fields = lines.get(0).split("\t", -1);
			assertEquals(7, fields.length);
			assertNotEquals("", fields[0]);
			assertNotEquals("9e143c2c-9f5b-462a-ac92-5bebf8a9ca03", fields[0]);
			assertEquals("", fields[1]);

			assertEquals("1", fields[2]);
			assertEquals("21000210109", fields[3]);
			assertEquals("271000210107", fields[4]);
			assertEquals("675173018", fields[5]);
			assertEquals("900000000000548007", fields[6]);
		}
	}

	private static class MockResourceManager extends ResourceManager {

		public MockResourceManager(ResourceConfiguration resourceConfiguration, ResourceLoader cloudResourceLoader) {
			super(resourceConfiguration, cloudResourceLoader);
		}

		public InputStream readResourceStream(String fullPath) throws IOException {
			fullPath = fullPath.substring(fullPath.indexOf("/"));
			File file = new File(fullPath);
			if (file.exists() && file.canRead()) {
				return new FileInputStream(fullPath);
			} else {
				return null;
			}
		}

		public InputStream readResourceStreamOrNullIfNotExists(String fullPath) throws IOException {
			return this.readResourceStream(fullPath);
		}
	}
}
