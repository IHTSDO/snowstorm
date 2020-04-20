# Working with the FHIR API

## Design Overview

Snowstorms's FHIR capabilities are managed using the HAPI Server package, which brings a number of benefits such as automatic creation of the Capabilities resource. It also means that future releases of the FHIR Specification will not require code changes by SNOMED International as we'll be able migrate to the next version of HAPI.

## Documentation

Unfortunately HAPI does not easily support a Swagger interface for testing, but if you are used to working with **[Postman](https://www.getpostman.com/downloads/)**, here is a Postman project to try out some of the FHIR API calls.

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/4aa97fbcc6a6ccd0e94c)

You can also find a description of the capabilities here - https://documenter.getpostman.com/view/462462/S1TVXJ3k

## Testing

In a default installation, the FHIR endpoints can be found at: http://localhost:8080/fhir  although there is no operation there, so you could try one of these calls:

### Server Capabilities
http://localhost:8080/fhir/metadata

### Terminology Capabilities
http://localhost:8080/fhir/metadata?mode=terminology

## Code System

### Code System Lookups

#### Code System Lookup of Clinical Finding
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=404684003

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)|
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=427623005

#### Code System Lookup of medicinal product including normalForm and sufficientlyDefined properties.  Properties are listed here: [https://www.hl7.org/fhir/snomedct.html#props]
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=322236009&property=normalForm&property=sufficientlyDefined

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)| in Swedish Extension
####  Curl example allows use of language headers to specify Swedish language. NB Ensure use of single quotes in URL to avoid $lookup being treated as a variable by Unix shell
curl -i -H 'Accept-Language: sv' 'http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/45991000052106&code=427623005'

### Code System Validate Code

#### Code System validate-code
http://localhost:8080/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|404684003

### Code System Subsumes

#### Check if 195967001 |Asthma (disorder)| is a type of 50043002 |Disorder of respiratory system (disorder)|
http://localhost:8080/fhir/CodeSystem/$subsumes?codingA=http://snomed.info/sct/900000000000207008|50043002&codingB=http://snomed.info/sct/900000000000207008|195967001

#### Alternatively specify the system and (optionally) version separately using code rather than coding
http://localhost:8080/fhir/CodeSystem/$subsumes?codeA=50043002&codeB=195967001&system=http://snomed.info/sct/900000000000207008&version=20190731

## ValueSet search, create, replace, update and delete

#### Upload or update a valueset json file:
curl -i --request PUT "http://localhost:8080/fhir/ValueSet/address-use" \
--header "Content-Type: application/fhir+json" \
-d @exampleVs.json

#### Expand a valueset which has an ECL expression in its url element
http://localhost:8080/fhir/ValueSet/chronic-disease/$expand?includeDesignations=true

#### Recover a valueset eg address-use
http://localhost:8080/fhir/ValueSet/address-use?_format=json

#### Delete a valueset
curl --request DELETE "http://localhost:8080/fhir/ValueSet/address-use"

#### Recover all stored ValueSets
http://localhost:8080/fhir/ValueSet

#### Search for ValueSets meeting specified criteria
http://localhost:8080/fhir/ValueSet?&lt;name&gt;=&lt;value&gt;
Note that the "code" parameter is not supported.  It is considered "TooCostly" as it would requiring expanding (or caching the expansion of) all known ValueSets to see if the specified code was included.

## ValueSet Expansion
### Implicit ValueSets (ie intensionally defined). 
See  [https://www.hl7.org/fhir/snomedct.html#implicit]

#### Expansion of an intensionally defined value set using ECL
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003

#### Expansion of an intensionally defined value set using ECL against a specific edition/version
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&system-version=system-version=http://snomed.info/sct/900000000000207008/version/20190731

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



### ValueSet validate-code
#### Validate a code against a known ValueSet expansion
http://localhost:8080/fhir/ValueSet/gps/$validate-code?coding=http://snomed.info/sct|840539006

#### Validate a code against an implicit valueset, also checking display term
http://localhost:8080/fhir/ValueSet/$validate-code?url=http://snomed.info/sct?fhir_vs=ecl/<<34014006 |Viral disease|&coding=http://snomed.info/sct|840539006&dsiplay=COVID-19

#### Validate a code against an implicit valueset expanded against a specific SNOMED release
http://localhost:8080/fhir/ValueSet/$validate-code?url=http://snomed.info/sct/900000000000207008/version/20200309?fhir_vs=ecl/<<34014006 |Viral disease|&coding=http://snomed.info/sct|840539006

#### Validate a code against an implicit valueset expanded against a specific SNOMED release - alternative format
http://localhost:8080/fhir/ValueSet/$validate-code?codeSystem=http://snomed.info/sct/900000000000207008/version/20190731&url=http://snomed.info/sct?fhir_vs=ecl/%3C%3C34014006%20|Viral%20disease|&code=840539006

## Concept Maps
#### Historical Association find the "SAME AS" target for inactivated concept 
localhost:8080/fhir/ConceptMap/$translate?code=134811001&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=900000000000527005

#### Find ICD-10 Map target for 254153009 |Familial expansile osteolysis (disorder)|
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://hl7.org/fhir/sid/icd-10&url=http://snomed.info/sct?fhir_cm=447562003

#### Find ICD-O Map target for 772292003 |High grade glioma (morphologic abnormality)|
http://localhost:8080/fhir/ConceptMap/$translate?code=772292003&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://hl7.org/fhir/sid/icd-o&url=http://snomed.info/sct?fhir_cm=446608001

#### Find SNOMED CT concepts that have a particular ICD-10 code as their map target.  This reverse lookup is not medically safe, as the SI ICD-10 map is unidirectional by design.
http://localhost:8080/fhir/ConceptMap/$translate?code=Q79.8&system=http://hl7.org/fhir/sid/icd-10&source=http://hl7.org/fhir/sid/icd-10&target=http://snomed.info/sct

#### Find all Maps target for 254153009 |Familial expansile osteolysis (disorder)| - note fhir_cm value is left blank so all refsets are potentially returned.
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=

------

#### Notes on output:

The API will return either JSON or XML depending on what's specified in the 'Accept' header.  Because most browsers specify both HTML and XML as acceptable, where HTML is detected, the server will assume a browser is being used and return JSON unless a format parameter is used.   It is no longer necessary to include &_format=json in URLs when testing via a browser.   When no 'Accept' header is specified, JSON will again be used by default.
