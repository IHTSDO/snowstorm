# Snowstorm FHIR Terminology Server
The Snowstorm HL7® FHIR® API implements the terminology module, version R4. The implementation makes use of the excellent open source HAPI-FHIR library. 
All resources are persisted to Elasticsearch, a fast horizontally-scalable index.

## Code Systems
### SNOMED CT
Snowstorm is a specialist SNOMED CT terminology server, and as such implements SNOMED CT specific functionality 
including the SNOMED CT URI, filters, properties, implicit value sets, and implicit concept maps. 

_Please Note: The native Snowstorm API must be used to create code systems and import content for SNOMED CT._ 

### LOINC
The LOINC code system is supported. 
A LOINC package can be imported using the [HAPI-FHIR CLI tool](https://hapifhir.io/hapi-fhir/docs/tools/hapi_fhir_cli.html) with the following command:
```
hapi-fhir-cli upload-terminology -d Loinc_2.72.zip -v r4 -t http://localhost:8080/fhir -u http://loinc.org
```

### ICD-10 (International Version)
The international version of ICD-10 is supported. This distribution uses the CLAML format. 
An ICD-10 package can be imported using the [HAPI-FHIR CLI tool](https://hapifhir.io/hapi-fhir/docs/tools/hapi_fhir_cli.html) with the following command: 
```
hapi-fhir-cli upload-terminology -d icdClaML2019ens.zip -v r4 -t http://localhost:8080/fhir -u http://hl7.org/fhir/sid/icd-10
```

### ICD-10-CM (US Version)
ICD-10-CM, the US version of ICD-10, is supported. This distribution uses a tabular xml format. 
An ICD-10-CM package can be imported using the [HAPI-FHIR CLI tool](https://hapifhir.io/hapi-fhir/docs/tools/hapi_fhir_cli.html) with the following command:
```
hapi-fhir-cli upload-terminology -d icd10cm_tabular_2021.xml -v r4 -t http://localhost:8080/fhir -u http://hl7.org/fhir/sid/icd-10-cm
```

### Custom Code Systems
Any other code system can be imported and used if it can be transformed into the custom code system format.
This format allows code system properties and codes with a single display term, simple hierarchy and properties.

The parents and children of codes will be listed during lookup and subsumption testing will work if the code system "hierarchyMeaning" has a value of "is-a" when imported.

An example custom code system can be found under [docs/fhir-resources/custom_code_system](fhir-resources/custom_code_system). 

A custom code system zip package can be imported using the [HAPI-FHIR CLI tool](https://hapifhir.io/hapi-fhir/docs/tools/hapi_fhir_cli.html). 
Please assign a unique URL for the code system. Here is an example import command:
```
hapi-fhir-cli upload-terminology -d custom_code_system.zip -v r4 -t http://localhost:8080/fhir -u http://example.com/lab-codes
```

## Examples
Use Postman to try out some of the FHIR API calls.

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/46ece7cbc44cffed9f26)

You can find a description of the capabilities here - https://documenter.getpostman.com/view/7704601/TzCTYk3R

You can also find the HTTP requests in [the fhir-requests.http file](fhir-requests.http) in this repo.

## Testing

By default the base of the FHIR API is http://localhost:8080/fhir

### Server Capabilities
http://localhost:8080/fhir/metadata

### Terminology Capabilities
http://localhost:8080/fhir/metadata?mode=terminology

### Code System

#### [CodeSystem Instances](fhir-resources/code-system-instances.md)

#### [CodeSystem Lookup](fhir-resources/code-system-lookup.md)

#### [CodeSystem Validate Code](fhir-resources/code-system-validate-code.md)

#### [CodeSystem Subsumes](fhir-resources/code-system-subsumes.md)

### ValueSet

#### [ValueSet search, create, replace, update and delete](fhir-resources/valueset-scrud.md)

#### [ValueSet Expand](fhir-resources/valueset-expansion.md)

#### [ValueSet Validate-Code](fhir-resources/valueset-validate-code.md)

### Concept Maps

#### [ConceptMap Translate](fhir-resources/concept-map.md)

------

#### Notes on output
The API will return either JSON or XML depending on what's specified in the 'Accept' header.  Because most browsers specify both HTML and XML as acceptable, where HTML is detected, the server will assume a browser is being used and return JSON unless a format parameter is used.   It is no longer necessary to include &_format=json in URLs when testing via a browser.   When no 'Accept' header is specified, JSON will again be used by default.

#### Notes on unversioned content
Unversioned SNOMED CT content that exists on the Snowstorm code system branch can be accessed using system `http://snomed.info/xsct`. For example:
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sctx/45991000052106?fhir_vs=isa/27624003&designation=sv
