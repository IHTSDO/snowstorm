# Working with the FHIR API

**Please be aware that the Snowstorm FHIR interface is in development, with more focus on the full implementation in early 2019.**

## Design Overview

Snowstorms's FHIR capabilities are managed using the HAPI Server package, which brings a number of benefits such as automatic creation of the Capabilities resource. It also means that future releases of the FHIR Specification will not require code changes by SNOMED International as we'll be able migrate to the next version of HAPI.

## Testing
Unfortunately HAPI does not appear to allow us to supply a Swagger interface for testing, and if you find a way to make it work do please let us know!

In a default installation, the FHIR endpoints can be found at: http://localhost:8080/fhir  although there is no operation there, so you could try one of these calls:

#### Server Capabilities
http://localhost:8080/fhir/metadata?_format=json

#### Code System Lookup of Clinical Finding
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=404684003&_format=json

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)|
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=427623005&_format=json

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)| in Swedish Extension
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/45991000052106&code=427623005&_format=json

#### Expansion of an intensionally defined value set
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&_format=json

#### Expansion of an intensional value set against the Swedish Edition
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/<<27624003&_format=json

#### Paging through 10 at a time, request the 2nd page
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&count=10&offset=1&_format=json

#### Term filtering - ValueSet of all <<763158003 |Medicinal product (product)| containing the word aspirin.  This is not case sensitive.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<763158003&filter=Aspirin&_format=json

#### Refset - list all SNOMED concepts mapped to ICD-O  (ECL here is ^446608001 |ICD-O simple map reference set (foundation metadata concept)|)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%5E446608001&count=20&_format=json

#### Historical Association find the "SAME AS" target for inactivated concept 
localhost:8080/fhir/ConceptMap/$translate?code=134811001&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=900000000000527005&_format=json

#### Find ICD-10 Map target for 254153009 |Familial expansile osteolysis (disorder)|
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=ICD-10&url=http://snomed.info/sct?fhir_cm=447562003&_format=json

#### Find all Maps target for 254153009 |Familial expansile osteolysis (disorder)| - note fhir_cm value is left blank so all refsets are potentially returned.
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=&_format=json
