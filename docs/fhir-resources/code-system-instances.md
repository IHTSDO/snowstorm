## CodeSystem Instances

#### List all available Code System instances (modules and versions)
http://localhost:8080/fhir/CodeSystem

#### Get detail on a particular Code System Instance
http://localhost:8080/fhir/CodeSystem/sct_900000000000207008_20200309

#### Search for all CodeSystem instances with a particular version
http://localhost:8080/fhir/CodeSystem?version=20190731

#### Search for all CodeSystem instances with particular text anywhere in the publisher field
http://localhost:8080/fhir/CodeSystem?publisher:contains=SNOMED

### CodeSystem Operations

All the operations that can be performed directly against the CodeSystem endpoint (specifying a SNOMED Edition via a system or version parameter), can also be performed using the CodeSystem instance indentifier directly, instead of the equivalent parameters. There is no material difference between these two apporaches and the results are expected to be identical.

#### Validate Code against a particular Code System Instance
http://localhost:8080/fhir/CodeSystem/sct_900000000000207008_20200309/$validate-code?code=840539006
See [CodeSystem Validate Code](fhir-resources/code-system-validate-code.md) for more examples of validate code

#### Lookup a Code against a particular Code System Instance
http://localhost:8080/fhir/CodeSystem/sct_900000000000207008_20200309/$lookup?code=840539006
See [CodeSystem Lookup](fhir-resources/code-system-lookup.md) for more examples of code lookup

#### Check subsumption relationship in a particular Code System Instance
http://localhost:8080/fhir/CodeSystem/sct_900000000000207008_20200309/$subsumes?codeA=399144008&codeB=73211009
See [CodeSystem Subsumes](fhir-resources/code-system-subsumes.md) for more examples of subsumption testing