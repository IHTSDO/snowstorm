# Working with the FHIR API

## Design Overview

Snowstorms's FHIR capabilities are managed using the HAPI Server package, which brings a number of benefits such as automatic creation of the Capabilities resource. It also means that future releases of the FHIR Specification will not require code changes by SNOMED International as we'll be able migrate to the next version of HAPI.

## Documentation

Unfortunately HAPI does not easily support a Swagger interface for testing, but if you are used to working with **[Postman](https://www.getpostman.com/downloads/)**, here is a Postman project to try out some of the FHIR API calls.

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/46ece7cbc44cffed9f26)

You can find a description of the capabilities here - https://documenter.getpostman.com/view/7704601/TzCTYk3R

You can also find the HTTP requests in [the fhir-requests.http file](fhir-requests.http) in this repo.

## Testing

In a default installation, the FHIR endpoints can be found at: http://localhost:8080/fhir  although there is no operation there, so you could try one of these calls:

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

#### [ValueSet Expansion](fhir-resources/valueset-expansion.md)

#### [ValueSet Validate Code](fhir-resources/valueset-validate-code.md)

### Concept Maps

#### [ValueSet ConceptMap](fhir-resources/concept-map.md)

------

#### Notes on output
The API will return either JSON or XML depending on what's specified in the 'Accept' header.  Because most browsers specify both HTML and XML as acceptable, where HTML is detected, the server will assume a browser is being used and return JSON unless a format parameter is used.   It is no longer necessary to include &_format=json in URLs when testing via a browser.   When no 'Accept' header is specified, JSON will again be used by default.

#### Notes on unversioned content
The FHIR specification has no notion of working with unversioned, unpublished content as a content provider might wish to do during an authoring cycle.   As a 'straw man' solution for discussion, a magic string value of UNVERSIONED is being allowed, which will cause the request to look at the "daily build" branch, or whatever we think of as "MAIN" for that particular code system.   This will work for both CodeSystem $lookup and ValueSet $expand operations eg
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106/version/UNVERSIONED?fhir_vs=isa/27624003&designation=sv
