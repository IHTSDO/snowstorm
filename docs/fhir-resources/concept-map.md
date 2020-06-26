## Concept Maps

#### Historical Association find the "SAME AS" target for inactivated concept 
localhost:8080/fhir/ConceptMap/$translate?code=134811001&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=900000000000527005

#### Find ICD-10 Map target for 254153009 |Familial expansile osteolysis (disorder)|
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://hl7.org/fhir/sid/icd-10&url=http://snomed.info/sct?fhir_cm=447562003

#### Find ICD-10-CM Map for 365753005 |Finding of presence of drug (finding)| using specific edition & version (because the ICD-10-CM Map exists in the US Edition)
http://localhost:8080/fhir/ConceptMap/$translate?code=365753005&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://hl7.org/fhir/sid/icd-10&url=http://snomed.info/sct/731000124108/version/20200301?fhir_cm=6011000124106

Note that the version parameter itself is not supported because the edition of the source concept is irrelevant.  The response is dependent on the location of the map refset itself, which should be specified in the url parameter as shown here.

#### Find ICD-O Map target for 772292003 |High grade glioma (morphologic abnormality)|
http://localhost:8080/fhir/ConceptMap/$translate?code=772292003&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://hl7.org/fhir/sid/icd-o&url=http://snomed.info/sct?fhir_cm=446608001

#### Find SNOMED CT concepts that have a particular ICD-10 code as their map target.  This reverse lookup is not medically safe, as the SI ICD-10 map is unidirectional by design.
http://localhost:8080/fhir/ConceptMap/$translate?code=Q79.8&system=http://hl7.org/fhir/sid/icd-10&source=http://hl7.org/fhir/sid/icd-10&target=http://snomed.info/sct

#### Find all Maps target for 254153009 |Familial expansile osteolysis (disorder)| - note fhir_cm value is left blank so all refsets are potentially returned.
http://localhost:8080/fhir/ConceptMap/$translate?code=254153009&system=http://snomed.info/sct&source=http://snomed.info/sct?fhir_vs&target=http://snomed.info/sct?fhir_vs&url=http://snomed.info/sct?fhir_cm=