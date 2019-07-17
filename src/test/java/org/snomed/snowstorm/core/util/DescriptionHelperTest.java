package org.snomed.snowstorm.core.util;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

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
}
