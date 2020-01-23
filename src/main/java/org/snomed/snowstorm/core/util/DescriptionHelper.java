package org.snomed.snowstorm.core.util;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DescriptionHelper {

	public static TermLangPojo getFsnDescriptionTermAndLang(Set<Description> descriptions, List<LanguageDialect> languageDialects) {
		return getFsnDescription(descriptions, languageDialects).map(d -> new TermLangPojo(d.getTerm(), d.getLang())).orElse(new TermLangPojo());
	}

	public static Optional<Description> getFsnDescription(Set<Description> descriptions, List<LanguageDialect> languageDialects) {
		return Optional.ofNullable(getBestDescription(descriptions, languageDialects, Concepts.FSN));
	}

	public static TermLangPojo getPtDescriptionTermAndLang(Set<Description> descriptions, List<LanguageDialect> languageDialects) {
		return getPtDescription(descriptions, languageDialects).map(d -> new TermLangPojo(d.getTerm(), d.getLang())).orElse(new TermLangPojo());
	}

	public static Optional<Description> getPtDescription(Set<Description> descriptions, List<LanguageDialect> languageDialects) {
		return Optional.ofNullable(getBestDescription(descriptions, languageDialects, Concepts.SYNONYM));
	}

	private static Description getBestDescription(Set<Description> descriptions, List<LanguageDialect> languageDialects, String descriptionType) {
		if (languageDialects == null) {
			return null;
		}

		// Try each LanguageDialect in given order to match descriptions
		for (LanguageDialect languageDialect : languageDialects) {
			for (Description description : descriptions) {
				if (description.isActive()
						&& descriptionType.equals(description.getTypeId())
						&& description.getLang().equals(languageDialect.getLanguageCode())) {

					if (languageDialect.getLanguageReferenceSet() != null) {
						if (description.hasAcceptability(Concepts.PREFERRED, languageDialect.getLanguageReferenceSet().toString())) {
							return description;
						}
					} else {
						// Preferred in any language reference set
						if (description.hasAcceptability(Concepts.PREFERRED)) {
							return description;
						}
					}
				}
			}
		}

		return null;
	}

	public static String foldTerm(String term, Set<Character> charactersNotFolded) {
		if (charactersNotFolded == null) {
			return term;
		}
		char[] chars = term.toLowerCase().toCharArray();
		char[] charsFolded = new char[chars.length * 2];

		// Fold all characters
		int charsFoldedOffset = 0;
		try {
			for (int i = 0; i < chars.length; i++) {
				if (charactersNotFolded.contains(chars[i])) {
					charsFolded[charsFoldedOffset] = chars[i];
				} else {
					int length = ASCIIFoldingFilter.foldToASCII(chars, i, charsFolded, charsFoldedOffset, 1);
					if (length != charsFoldedOffset + 1) {
						charsFoldedOffset = length - 1;
					}
				}
				charsFoldedOffset++;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw e;
		}
		return new String(charsFolded, 0, charsFoldedOffset);
	}

}
