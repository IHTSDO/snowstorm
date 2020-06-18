## ValueSet Expansion

### Implicit ValueSets (ie intensionally defined). 
See  [https://www.hl7.org/fhir/snomedct.html#implicit]

#### Expansion of an intensionally defined value set using ECL
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003

#### Expansion of an intensionally defined value set using ECL against a specific edition/version
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&system-version=http://snomed.info/sct/900000000000207008/version/20190731

#### Expansion of an intensionally defined value set using ISA
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/27624003

#### Expansion of an intensionally defined value set using refset (ICD-10 complex map)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset/447562003

#### Expansion of an intensionally defined value set using nothing!  Returns all concepts.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs

#### Expansion of an intensional value set against the Swedish Edition, including synonyms
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&includeDesignations=true&count=10&designation=sv&designation=en

#### Expansion of an intensional value set against the Swedish Edition, specifying Swedish Language for the display field (normally the server returns the FSN, which is in English in the Swedish Edition).
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&count=10&displayLanguage=sv

#### Expansion specifying a language reference set for the designation.  However, since includeDesigntations is set to false, this results in a very minimal return with just the display returning as "Acetaminophen" due to being the US preferred term.   Note that setting includeDesignations=true will return both preferred and acceptable terms
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/322280009&designation=http://snomed.info/sct|900000000000509007&includeDesignations=false

#### Paging through 10 at a time, request the 2nd page
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&count=10&offset=1

#### Term filtering - ValueSet of all <<763158003 |Medicinal product (product)| containing the word aspirin.  This is not case sensitive.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<763158003&filter=Aspirin

#### Refset - list all SNOMED concepts mapped to ICD-O  (ECL here is ^446608001 |ICD-O simple map reference set (foundation metadata concept)|)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%5E446608001&count=20


