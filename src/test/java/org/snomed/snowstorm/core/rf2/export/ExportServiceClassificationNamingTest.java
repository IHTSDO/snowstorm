package org.snomed.snowstorm.core.rf2.export;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.PackageType;
import org.snomed.snowstorm.core.rf2.RF2Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExportServiceClassificationNamingTest {

	@Test
	void legacyNamingHasNoPackageTypeTag() {
		assertNull(ExportService.getClassificationPackageTypeTag(null));
		assertNull(ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT-BE", null, PackageType.EXTENSION)));
		assertNull(ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT-BE", true, PackageType.EXTENSION)));
	}

	@Test
	void newNamingUsesPackageTypeTag() {
		assertEquals("Edition", ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT", false, PackageType.EDITION)));
		assertEquals("Extension", ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT-BE", false, PackageType.EXTENSION)));
		assertEquals("Derivative", ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT-NUVA", false, PackageType.DERIVATIVE)));
		assertNull(ExportService.getClassificationPackageTypeTag(codeSystem("SNOMEDCT-XX", false, null)));
	}

	@Test
	void fileNameWithPackageTypeTagAfterContentType() {
		ExportService.RF2FileNaming edition = new ExportService.RF2FileNaming("INT", "Edition");
		assertEquals("sct2_ConceptEdition_Delta_INT_20270701.txt", edition.fileName("sct2_Concept_", RF2Type.DELTA, "20270701"));
		assertEquals("sct2_RelationshipConcreteValuesEdition_Delta_INT_20270701.txt", edition.fileName("sct2_RelationshipConcreteValues_", RF2Type.DELTA, "20270701"));
	}

	@Test
	void refsetFileNameWithPackageTypeTagBeforeReleaseType() {
		ExportService.RF2FileNaming edition = new ExportService.RF2FileNaming("INT", "Edition");
		assertEquals("sct2_sRefset_OWLExpression733073007EditionDelta_INT_20270701.txt", edition.fileName("sct2_sRefset_OWLExpression733073007", RF2Type.DELTA, "20270701"));
		assertEquals("der2_cissccRefset_MRCMAttributeDomainEditionSnapshot_INT_20270701.txt", edition.fileName("der2_cissccRefset_MRCMAttributeDomain", RF2Type.SNAPSHOT, "20270701"));
		assertEquals("der2_cRefset_DICOMLanguageEditionSnapshot_INT_20240701.txt", edition.fileName("der2_cRefset_DICOMLanguage", RF2Type.SNAPSHOT, "20240701"));
	}

	@Test
	void fileNameWithoutPackageTypeTag() {
		ExportService.RF2FileNaming legacy = new ExportService.RF2FileNaming("BE", null);
		assertEquals("sct2_Concept_Delta_BE_20270701.txt", legacy.fileName("sct2_Concept_", RF2Type.DELTA, "20270701"));
		assertEquals("der2_cRefset_LanguageSnapshot_BE_20270701.txt", legacy.fileName("der2_cRefset_Language", RF2Type.SNAPSHOT, "20270701"));
	}

	private static CodeSystem codeSystem(String shortName, Boolean legacyNamingConvention, PackageType packageType) {
		CodeSystem codeSystem = new CodeSystem(shortName, "MAIN/" + shortName);
		codeSystem.setLegacyNamingConvention(legacyNamingConvention);
		codeSystem.setPackageType(packageType);
		return codeSystem;
	}
}
