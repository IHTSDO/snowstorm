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
		assertEquals("Hjärta", DescriptionHelper.foldTerm("Hjärta", charactersNotFolded));

		// Swedish character é is folded
		assertEquals("Lasegues test", DescriptionHelper.foldTerm("Laségues test", charactersNotFolded));
	}
}
