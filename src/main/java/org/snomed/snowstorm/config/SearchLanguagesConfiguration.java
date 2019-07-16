package org.snomed.snowstorm.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SearchLanguagesConfiguration {

	private Map<String, String> charactersNotFolded = new HashMap<>();
	private Map<String, Set<Character>> charactersNotFoldedSets;

	public Map<String, String> getCharactersNotFolded() {
		return charactersNotFolded;
	}

	public Map<String, Set<Character>> getCharactersNotFoldedSets() {
		if (charactersNotFoldedSets == null) {
			synchronized (this) {
				if (charactersNotFoldedSets == null) {
					charactersNotFoldedSets = new HashMap<>();
					for (Map.Entry<String, String> entry : charactersNotFolded.entrySet()) {
						charactersNotFoldedSets.put(entry.getKey(), toCharSet(entry.getValue()));
					}
				}
			}
		}
		return charactersNotFoldedSets;
	}

	private Set<Character> toCharSet(String s) {
		Set<Character> set = new HashSet<>();
		for (char c : s.toCharArray()) {
			set.add(c);
		}
		return set;
	}
}
