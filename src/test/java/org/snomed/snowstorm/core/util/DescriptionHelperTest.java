package org.snomed.snowstorm.core.util;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.rest.ControllerHelper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.GB_EN_LANG_REFSET;
import static org.snomed.snowstorm.core.data.domain.Concepts.US_EN_LANG_REFSET;

public class DescriptionHelperTest {

	@Test
	public void foldTerm() {
		HashSet<Character> charactersNotFolded = Sets.newHashSet('å', 'ä', 'ö', 'Å', 'Ä', 'Ö');

		// Swedish character ä not folded
		assertEquals("hjärta", DescriptionHelper.foldTerm("Hjärta", charactersNotFolded));

		// Swedish character é is folded
		assertEquals("lasegues test", DescriptionHelper.foldTerm("Laségues test", charactersNotFolded));

		// æ is folded to ae
		assertEquals("spaelsau sheep breed (organism) spaelsau", DescriptionHelper.foldTerm("Spælsau sheep breed (organism) Spælsau", charactersNotFolded));
	}

	@Test
	public void combinedCharactersNotFolded() {
		HashSet<Character> charactersNotFolded = Sets.newHashSet('æ');

		// æ is not folded
		assertEquals("spælsau sheep breed (organism) spælsau", DescriptionHelper.foldTerm("Spælsau sheep breed (organism) Spælsau", charactersNotFolded));
	}

	@Test
	public void testGetPtDescription() {
		String danishLanguageReferenceSet = "554461000005103";

		Set<Description> descriptions = Sets.newHashSet(
				// FSN
				new Description("Jet airplane, device (physical object)").setTypeId(Concepts.FSN),

				// EN-US PT
				new Description("Jet airplane").setTypeId(Concepts.SYNONYM).addLanguageRefsetMember(US_EN_LANG_REFSET, Concepts.PREFERRED),

				// EN-GB PT
				// ... British English aeroplane, from French aéroplane, from Ancient Greek ἀερόπλανος (aeróplanos, “wandering in air”).
				// I love the history of the English language!
				new Description("Jet aeroplane").setTypeId(Concepts.SYNONYM).addLanguageRefsetMember(GB_EN_LANG_REFSET, Concepts.PREFERRED),

				new Description("jetfly").setTypeId(Concepts.SYNONYM).setLang("da").addLanguageRefsetMember(danishLanguageReferenceSet, Concepts.PREFERRED)
		);

		assertEquals("en-X-900000000000509007,en-X-900000000000508004,en", Config.DEFAULT_ACCEPT_LANG_HEADER);
		assertEquals("Jet airplane", getPtTerm(null, descriptions));
		assertEquals("Jet airplane", getPtTerm(Config.DEFAULT_ACCEPT_LANG_HEADER, descriptions));
		assertEquals("Jet aeroplane", getPtTerm("en-X-" + GB_EN_LANG_REFSET, descriptions));
		assertEquals("Fallback on US english defaults.", "Jet airplane", getPtTerm("en-X-" + danishLanguageReferenceSet, descriptions));
		assertEquals("Fallback on GB english because of header.", "Jet aeroplane", getPtTerm("en-X-" + danishLanguageReferenceSet + ",en-X-" + GB_EN_LANG_REFSET, descriptions));
		assertEquals("jetfly", getPtTerm("da-X-" + danishLanguageReferenceSet, descriptions));
	}

	private String getPtTerm(String acceptLanguageHeader, Set<Description> descriptions) {
		return DescriptionHelper.getPtDescription(descriptions, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader)).orElseGet(Description::new).getTerm();
	}
}
