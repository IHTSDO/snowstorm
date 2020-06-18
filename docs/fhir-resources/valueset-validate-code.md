## ValueSet validate-code

#### Validate a code against a known ValueSet expansion
http://localhost:8080/fhir/ValueSet/gps/$validate-code?coding=http://snomed.info/sct|840539006

#### Validate a code against an implicit valueset, also checking display term
http://localhost:8080/fhir/ValueSet/$validate-code?url=http://snomed.info/sct?fhir_vs=ecl/<<34014006 |Viral disease|&coding=http://snomed.info/sct|840539006&display=COVID-19

#### Validate a code against an implicit valueset expanded against a specific SNOMED release
http://localhost:8080/fhir/ValueSet/$validate-code?url=http://snomed.info/sct/900000000000207008/version/20200309?fhir_vs=ecl/<<34014006 |Viral disease|&coding=http://snomed.info/sct|840539006

#### Validate a code against an implicit valueset expanded against a specific SNOMED release - alternative format
http://localhost:8080/fhir/ValueSet/$validate-code?codeSystem=http://snomed.info/sct/900000000000207008/version/20190731&url=http://snomed.info/sct?fhir_vs=ecl/%3C%3C34014006%20|Viral%20disease|&code=840539006
