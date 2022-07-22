## FHIR ConceptMap Translate

#### Historical Association find the "SAME AS" target for inactivated concept 
http://localhost:8080/fhir/ConceptMap/$translate?code=134811001&system=http://snomed.info/sct&url=http://snomed.info/sct?fhir_cm=900000000000527005

#### Find ICD-10 Map target for 254153009 |Familial expansile osteolysis (disorder)|
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&targetsystem=http://hl7.org/fhir/sid/icd-10&url=http://snomed.info/sct?fhir_cm=447562003

#### Find ICD-10-CM Map for 365753005 |Finding of presence of drug (finding)| using specific edition (the ICD-10-CM Map exists in the US Edition)
http://localhost:8080/fhir/ConceptMap/$translate?code=365753005&system=http://snomed.info/sct&version=http://snomed.info/sct/731000124108&&targetsystem=http://hl7.org/fhir/sid/icd-10-cm

#### Find SNOMED CT concepts that have a particular ICD-10 code as their map target.
#### This reverse lookup is not medically safe, as the SI ICD-10 map is unidirectional by design.
#### ECL 2.0 supports this. We can filter members of the ICD-10 map by target using: ^447562003 {{M mapTarget="Q79.8"}}
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%5E447562003{{M mapTarget="Q79.8"}}

#### Find all map targets for 254153009 |Familial expansile osteolysis (disorder)| - note that no "target", "targetsystem" or "url" have been set so all maps will be used.
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct
