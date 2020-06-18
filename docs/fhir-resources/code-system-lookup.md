## Code System Lookups

#### Code System Lookup of Clinical Finding
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=404684003

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)|
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=427623005

#### Code System Lookup of medicinal product including normalForm and sufficientlyDefined properties.  Properties are listed here: [https://www.hl7.org/fhir/snomedct.html#props]
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=322236009&property=normalForm&property=sufficientlyDefined

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)| in Swedish Extension
####  Curl example allows use of language headers to specify Swedish language. NB Ensure use of single quotes in URL to avoid $lookup being treated as a variable by Unix shell
curl -i -H 'Accept-Language: sv' 'http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/45991000052106&code=427623005'

