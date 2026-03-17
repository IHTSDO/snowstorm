package org.snomed.snowstorm.core.util;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.jetbrains.annotations.Nullable;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class DescriptionHelper {

	public static final Set<String> STANDARD_DESCRIPTION_TYPES = Set.of(Concepts.FSN, Concepts.SYNONYM, Concepts.TEXT_DEFINITION);

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

		// Description type must equal the requested type
		Predicate<String> typePredicate = descriptionType::equals;

		Description preferredSynonym = selectDescription(descriptions, languageDialects, typePredicate);
		if (preferredSynonym != null) return preferredSynonym;
		// No preferred description of the correct type found


		if (Concepts.SYNONYM.equals(descriptionType)) {
			// Extension may have added a synonym subtype (the case in Singapore)
			// Description must be preferred but the type could be anything that is not FSN, DEF or SYN
			Predicate<String> typePredicate2 = Predicate.not(STANDARD_DESCRIPTION_TYPES::contains);
			Description description = selectDescription(descriptions, languageDialects, typePredicate2);
			if (description != null) return description;
		}

		// Lang refset entries may be missing attempt to match against language code only
		for (LanguageDialect languageDialect : languageDialects) {
			if (languageDialect.getLanguageReferenceSet() == null) {
				// Fallback with no specific lang refset - pick description by type only in last ditch attempt.
				for (Description description : descriptions) {
					if (description.isActive()
							&& descriptionType.equals(description.getTypeId())
							&& description.getLang().equals(languageDialect.getLanguageCode())) {

						return description;
					}
				}
			}
		}

		return null;
	}

	private static @Nullable Description selectDescription(Set<Description> descriptions, List<LanguageDialect> languageDialects, Predicate<String> descriptionTypePredicate) {
		// Try each LanguageDialect in given order to match descriptions
		for (LanguageDialect languageDialect : languageDialects) {
			for (Description description : descriptions) {
				boolean test = descriptionTypePredicate.test(description.getTypeId());
				if (description.isActive()
						&& test
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

	public static String wildcardToCaseInsensitiveRegex(String term) {
		StringBuilder builder = new StringBuilder();
		for (char c : term.toCharArray()) {
			String s = String.valueOf(c);
			if (!s.toLowerCase().equals(s) || !s.toUpperCase().equals(s)) {
				// char is case-sensitive
				builder.append("[")
						.append(s.toLowerCase())
						.append(s.toUpperCase())
						.append("]");
			} else {
				builder.append(s);
			}
		}
		return builder.toString().replace("*", ".*");
	}
}
