## Languages and Dialects

A SNOMED CT edition can hold terms in several languages (the Belgian Edition has French, Dutch, German
and English), and several dialects of one language (US and GB English), each defined by a language
reference set. This page explains how the FHIR API decides which term to return.

### Asking for a language
`CodeSystem/$lookup`, `CodeSystem/$validate-code` and `ValueSet/$expand` accept either:

- the `displayLanguage` parameter (the FHIR standard way), or
- the HTTP `Accept-Language` header, which may list several languages.

If both are given, `displayLanguage` is used.

In `Accept-Language`, each language can carry a weight (`q`, from 0 to 1). A language with no weight counts
as 1.0, as the HTTP standard defines. Higher weights are preferred, and languages with equal weights are
taken in the order listed. For example, `Accept-Language: en;q=0.5, fr` prefers French.

### Language tags
| Tag | Meaning |
|---|---|
| `fr` | Any French term |
| `en-us`, `en-gb` | A dialect: Snowstorm uses the language reference set configured for it |
| `en-x-900000000000508004` | A language plus a specific language reference set, by SCTID |

### When no language is given
Snowstorm uses English: the US English preferred term, then GB English, then any English term.
This applies to every edition, including those whose main language isn't English.
For example, the Belgian Edition returns English displays unless French, Dutch or German is requested.

### What comes back
- `display` is the preferred term: the synonym marked preferred in the language reference set, because
  FHIR displays are meant to be the term clinicians use. It comes from the first requested language or
  dialect that has one.
- `$lookup`, and `$expand` with `includeDesignations=true`, also return the concept's designations, each with
  its `language` and `use` (Fully specified name `900000000000003001` or Synonym `900000000000013009`).

### Examples
#### French display from the Belgian Edition
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/11000172109?fhir_vs=ecl/22298006&displayLanguage=fr

#### Dutch display using the Accept-Language header
curl -H 'Accept-Language: nl' 'http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct/11000172109?fhir_vs=ecl/22298006'

#### Lookup in the Belgian Edition, returning designations in every language
http://localhost:8080/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&version=http://snomed.info/sct/11000172109&code=22298006

#### US and GB English dialects: "Acetaminophen" and "Paracetamol"
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/322280009&displayLanguage=en-us

http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/322280009&displayLanguage=en-gb

#### Choosing a language reference set by SCTID (GB English)
http://localhost:8080/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/322280009&displayLanguage=en-x-900000000000508004
