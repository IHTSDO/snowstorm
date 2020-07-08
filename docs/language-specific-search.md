# Language Specific Search Behaviour

## Expectations are language dependant 

Snowstorm can fold diacritic characters can into their simpler form to allow matching with or without the diacritics. For example in English some terms have é, for example "Déjà vu" but in English we expect the same search results when using characters with or without diacritics. For example, concept search using the term "Dejà" or "deja" should produce the same results.

However, the expectation of character folding is language dependant...

In Danish a term with the character "ø" should not be found when searching using "o". For example the concept "Ångstrøm" should not be found when searching using the term "Ångstrom".

Conversely in Swedish the character "ø" is not considered as an additional letter in the alphabet so the concept "Brønsted-Lowrys syra" should be found when searching using the term "Bronsted".

## Solution

The above all works as expected using the Snowstorm API. We have implemented configurable language specific character folding and written configuration for each of the commonly known national extensions. 
To see the list of configured languages, modify or add languages please see the "Search International Character Handling" section of the [Snowstorm configuration file](/src/main/resources/application.properties).

### Implementation notes
Each description term is indexed twice in Elasticsearch, in their raw form and with language specific character folding. When querying for matches we fold the search term using each configured strategy with a constraint to only match each folding against the intended language.
