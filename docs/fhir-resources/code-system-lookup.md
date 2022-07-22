## FHIR CodeSystem Lookup

#### Code System Lookup of Clinical Finding
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=404684003

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)|
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=427623005

#### Code System Lookup including as-yet-unpublished changes (using snomed.info/xsct)
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/xsct&code=427623005

#### Code System Lookup of medicinal product including normalForm and sufficientlyDefined properties.  Properties are listed here: [https://www.hl7.org/fhir/snomedct.html#props]
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=322236009&property=normalForm&property=sufficientlyDefined

#### Code System Lookup of 427623005 |Obstetric umbilical artery Doppler (procedure)| in Swedish Extension
#### Curl example allows use of language headers to specify Swedish language. NB Ensure use of single quotes in URL to avoid $lookup being treated as a variable by Unix shell
#### The displayLanguage request parameter can also be used as an alternative to the Accept-Language header.
curl -i -H 'Accept-Language: sv' 'http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/45991000052106&code=427623005'
