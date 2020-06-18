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
http://localhost:8080/fhir/ValueSet?name=value

Text matching modes are supported as detailed in the [FHIR specification](https://build.fhir.org/search.html#string).  The default search match is expected to be "StartsWith" eg 
http://localhost:8080/fhir/ValueSet?title=Reason

...and then if you want "Contains" or "Exact Match" you specify a modifier like this:
http://localhost:8080/fhir/ValueSet?publisher:exact=FHIR+project+team
or
http://localhost:8080/fhir/ValueSet?publisher:contains=team

The string parameters that can be searched in this way are: description, identifier, jurisdiction, name, published, reference, title.

 
Note that the "code" parameter is not supported.  It is considered "TooCostly" as it would requiring expanding (or caching the expansion of) all known ValueSets to see if the specified code was included.