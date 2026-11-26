package org.snomed.snowstorm.core.rf2.export;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.PackageType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportServiceClassificationNamingTest {

	@Test
	void legacyNamingKeepsGivenName() {
		assertEquals("INT", ExportService.getClassificationRF2Name(null, "INT"));
		assertEquals("INT", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-BE", null, PackageType.EXTENSION), "INT"));
		assertEquals("INT", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-BE", true, PackageType.EXTENSION), "INT"));
		assertEquals("BE", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-BE", true, PackageType.EXTENSION), "BE"));
	}

	@Test
	void newNamingAddsPackageType() {
		assertEquals("INTEdition", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT", false, PackageType.EDITION), "INT"));
		assertEquals("BEExtension", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-BE", false, PackageType.EXTENSION), "INT"));
		assertEquals("NUVADerivative", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-NUVA", false, PackageType.DERIVATIVE), "INT"));
	}

	@Test
	void newNamingWithoutPackageType() {
		assertEquals("INT", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT", false, null), "INT"));
		assertEquals("XX", ExportService.getClassificationRF2Name(codeSystem("SNOMEDCT-XX", false, null), "INT"));
	}

	private static CodeSystem codeSystem(String shortName, Boolean legacyNamingConvention, PackageType packageType) {
		CodeSystem codeSystem = new CodeSystem(shortName, "MAIN/" + shortName);
		codeSystem.setLegacyNamingConvention(legacyNamingConvention);
		codeSystem.setPackageType(packageType);
		return codeSystem;
	}
}
