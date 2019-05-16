# Working with the FHIR API

## Design Overview

Snowstorms's FHIR capabilities are managed using the HAPI Server package, which brings a number of benefits such as automatic creation of the Capabilities resource. It also means that future releases of the FHIR Specification will not require code changes by SNOMED International as we'll be able migrate to the next version of HAPI.

# Testing
Unfortunately HAPI does not easily support a Swagger interface for testing, and if you find a way to make it work do please let us know!

In a default installation, the FHIR endpoints can be found at: http://localhost:8080/fhir  although there is no operation there, so you could try one of these calls:

### Server Capabilities
http://localhost:8080/fhir/metadata?_format=json

### Code System Lookups

#### Code System Lookup of Clinical Finding
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=404684003&_format=json

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)|
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=427623005&_format=json

#### Code System Lookup of medicinal product including normalForm and sufficientlyDefined properties.  Properties are listed here: [https://www.hl7.org/fhir/snomedct.html#props]
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=322236009&property=normalForm&property=sufficientlyDefined&_format=json

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)| in Swedish Extension
####  Curl example allows use of language headers to specify Swedish language. NB Ensure use of single quotes in URL to avoid $lookup being treated as a variable by Unix shell
curl -i -H 'Accept-Language: sv' 'http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/45991000052106&code=427623005&_format=json'

## ValueSet Expansion
### Implicit ValueSets (ie intensionally defined). 
See  [https://www.hl7.org/fhir/snomedct.html#implicit]

#### Expansion of an intensionally defined value set using ECL
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&_format=json

#### Expansion of an intensionally defined value set using ISA
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/27624003&_format=json

#### Expansion of an intensionally defined value set using refset (ICD-10 complex map)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset/447562003&_format=json

#### Expansion of an intensionally defined value set using nothing!  Returns all concepts.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs&_format=json

#### Expansion of an intensional value set against the Swedish Edition, including synonyms
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&includeDesignations=true&count=10&designation=sv&designation=en&_format=json

#### Expansion of an intensional value set against the Swedish Edition, specifying Swedish Language for the display field (normally the server returns the FSN, which is in English in the Swedish Edition).
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&count=10&displayLanguage=sv&_format=json

#### Paging through 10 at a time, request the 2nd page
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&count=10&offset=1&_format=json

#### Term filtering - ValueSet of all <<763158003 |Medicinal product (product)| containing the word aspirin.  This is not case sensitive.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<763158003&filter=Aspirin&_format=json

#### Refset - list all SNOMED concepts mapped to ICD-O  (ECL here is ^446608001 |ICD-O simple map reference set (foundation metadata concept)|)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%5E446608001&count=20&_format=json

## Concept Maps
#### Historical Association find the "SAME AS" target for inactivated concept 
localhost:8080/fhir/ConceptMap/$translate?code=134811001&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=900000000000527005&_format=json

#### Find ICD-10 Map target for 254153009 |Familial expansile osteolysis (disorder)|
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=ICD-10&url=http://snomed.info/sct?fhir_cm=447562003&_format=json

#### Find all Maps target for 254153009 |Familial expansile osteolysis (disorder)| - note fhir_cm value is left blank so all refsets are potentially returned.
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=&_format=json
