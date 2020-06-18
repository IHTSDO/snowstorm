## Code System Subsumes

#### Check if 195967001 |Asthma (disorder)| is a type of 50043002 |Disorder of respiratory system (disorder)| in the January 2020 International Edition
http://localhost:8080//fhir//CodeSystem/$subsumes?system=http://snomed.info/sct&version=http://snomed.info/sct/900000000000207008/version/20200131&codeA=50043002&codeB=195967001

#### Alternatively specify the system and (optionally) version separately using code rather than coding
http://localhost:8080/fhir/CodeSystem/$subsumes?codeA=50043002&codeB=195967001&system=http://snomed.info/sct/900000000000207008&version=20190731