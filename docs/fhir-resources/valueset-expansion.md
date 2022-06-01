## ValueSet Expansion

### Implicit ValueSets (ie intensionally defined). 
See  [https://www.hl7.org/fhir/snomedct.html#implicit]

## GET Method Request Example(s)

#### Expansion of an intentionally defined value set using ECL
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003

#### Expansion of an intentionally defined value set using ECL against a specific edition/version
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&system-version=http://snomed.info/sct/900000000000207008/version/20190731

##### alternatively, the url parameter can also take a version URI.  This is an equivalent request:
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/900000000000207008/version/20190731?fhir_vs=ecl/<<27624003

#### Expansion of an intentionally defined value set using ECL against unpublished content (eg concepts newly created on the MAIN branch)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/xsct?fhir_vs=ecl/<<27624003

#### Expansion of an intentionally defined value set using ISA
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/27624003

#### Expansion including as-yet-unpublished changes to concepts
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/410607006&system-version=http://snomed.info/xsct&count=5

#### Expansion of an intentionally defined value set using refset (ICD-10 complex map)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset/447562003

#### Expansion of an intentionally defined value set using nothing!  Returns all concepts.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs

#### Expansion of an intentional value set against the Swedish Edition, including synonyms
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&includeDesignations=true&count=10&designation=sv&designation=en

#### Expansion of an intentional value set against the Swedish Edition, specifying Swedish Language for the display field (normally the server returns the FSN, which is in English in the Swedish Edition).
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/45991000052106?fhir_vs=ecl/%3C%3C27624003&count=10&displayLanguage=sv

#### Expansion specifying a language reference set for the designation.  However, since includeDesigntations is set to false, this results in a very minimal return with just the display returning as "Acetaminophen" due to being the US preferred term.   Note that setting includeDesignations=true will return both preferred and acceptable terms
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/322280009&designation=http://snomed.info/sct|900000000000509007&includeDesignations=false

#### Paging through 10 at a time, request the 2nd page.  Note that the offset is a number of elements, rather than a page offset, and must be a multiple of the page size ie count.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<27624003&count=10&offset=10

#### Term filtering - ValueSet of all <<763158003 |Medicinal product (product)| containing the word aspirin.  This is not case sensitive.
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<763158003&filter=Aspirin

#### Refset - list all SNOMED concepts mapped to ICD-O  (ECL here is ^446608001 |ICD-O simple map reference set (foundation metadata concept)|)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%5E446608001&count=20

## POST Method Request Example(s)

#### Expansion with filter operation using ECL

```
curl --request POST 'http://localhost:8080/fhir/ValueSet/$expand' \
--header 'Content-Type: application/json' \
--data-raw '{
    "resourceType": "Parameters",
    "parameter": [
        {
            "name": "valueSet",
            "resource": {
            	"resourceType": "ValueSet",
                "compose": {
                    "include": [
                        {
                            "filter": [
                                {
                                    "property": "concept",
                                    "op": "is-a",
                                    "value": "21351003"
                                }
                            ]
                        }
                    ]
                }
            }
        },
        {
            "name": "system-version",
            "valueString": "http://snomed.info/sct/900000000000207008/version/20200731"
        },
        {
            "name": "displayLanguage",
            "valueString": "en-GB"
        },
        {
            "name": "offset",
            "valueString": "1"
        },
        {
            "name": "count",
            "valueString": "5"
        }
    ]
}'
```

#### Expansion with compose which includes one or more other ValueSets

```
curl --request POST 'http://localhost:8080/fhir/ValueSet/$expand' \
--header 'Content-Type: application/json' \
--data-raw '{
    "resourceType": "Parameters",
    "parameter": [
        {
            "name": "valueSet",
            "resource": {
            	"resourceType": "ValueSet",
                "compose": {
                    "include": [
                        {
                             "valueSet": "http://snomed.info/sct?fhir_vs=ecl/<410607006"
                        }
                    ]
                }
            }
        }
    ]
}'
```
Note that the curl --location flag will cause a POST body to be removed if a 302 redirect is encountered, such as happens when an http call is redirected to https.
