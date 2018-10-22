# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). The change log format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).


## 2.1.0 Release - 2018-10-22

Snowstorm is now production ready as a read-only terminology server.

### Features
- Running with latest Elasticsearch server (6.4.2) is now tested recommended.
- Include Preferred Term (PT) in concepts returned from API.
- Translated content support.
  - Translated Fully Specified Name (FSN) and Preferred Term (PT) are returned
  against all API responses when language is set in the Accept-Language header.
- Add conceptActive filter to description search API.
- Search reference set members by mapTarget.

### Improvements
- Performance improvement when holding large change sets in MAIN branch.
- ReferenceComponent concept included in reference set member response.
- Creating import configuration checks branch path and code system.
- Better date formatting in branch created date and code system versions.
- Make concept lookup performance logging quieter.
- New flag to create code system version automatically during import.

## Fixes
- Inactive relationships excluded from integrity check.
- Correct path of Relationship API.
- Correct full integrity check branch mapping.
- Correct reference set member lookup branch mapping.
- Concept parents endpoint excludes inactive parent relationships.
- Allow creating code system on branches other than MAIN.
- RF2 import time logging calculation.
- Export configuration conceptsAndRelationshipsOnly defaults to false.



## 2.0.0 Release Candidate - 2018-09-19

This major version brings support for the new SNOMED Axiom component as well as
many productionisation fixes.

This version is ready for testing.

### Breaking
- Elasticsearch indexes must be recreated due to changes in their format.


### Features
- Support for new Axiom component type.
  - CRUD operations via concept browser format.
  - RF2 Import/Export.
  - Classification Service integration.
  - Axioms used in ECL queries against the stated form.
- New integrity check functions with API endpoints.
  - Runs automatically before promotion.
- Description search aggregations similar to SNOMED CT public browser.
  - Aggregations for module, semantic tag, language, concept reference set membership.
- Create Code Systems via REST API.
- Create Code System versions via REST API.
- All reference set members imported without the need for configuration.
- ICD, CTV3 and four MRCM reference sets added to default configuration for RF2 export.
- New released content RF2 patch API endpoint.

### Improvements
- Concurrency and branch locking improvements.
- Performance improvement for branches containing wide impacting semantic changes.
- Concept description search algorithm improvement.
- Classification Service client authentication.
- Component Identifier Service client authentication.
- Added support for ECL ancestor of wildcard.
- Update to latest Snomed Drools Engine version.
- Added software version API endpoint.
- Limit traceability logging to first 300 inferred changes.
- Allow microservices within the Snomed Single Sign-On gateway to access Snowstorm directly.
- Rows in RF2 delta only imported if effectiveTime is blank or greater than existing component.
- Concept search TSV download.
- Classification results TSV download.

### Fixes
- Many ECL fixes.
- Semantic index update fix for non "is a" relationships.
- Fixes for complete semantic index rebuild feature.
- Remove irrelevant concepts from branch merge review.
- Changes to axioms and historical associations included in conflict check.
- Bring pagination parameters in line with Snow Owl 5.x.
- Fix Authoring Form endpoint.
- Allow very large classification results to be saved.
- Fix classification status during save.
- Fix change type of classification results.
- Prevent unnecessary new versions of inactive language refset members.
- Fix RF2 export download headers.
- Better grouping and naming of API endpoints in Swagger interface.
- Set ELK as default reasoner in Swagger interface.
- Log traceability activity for branch merges.



## 1.1.0 Alpha - 2018-05-29
This second alpha release is another preview of Snowstorm in read-only mode.

### Breaking
- Elasticsearch indexes must be recreated due to changes in their format.

### Features
- Docker container option, see [Using Docker](docs/using-docker.md).

### Improvements
- Improved lexical search matching and sorting.
- Upgrade Snomed Drools validation engine.
- Improved CIS authentication.

### Fixes
- ECL fixes (some attributes were missing due to relationship sorting bug).
- MRCM endpoint pagination fix.



## 1.0.0 Alpha - 2018-04-12
This alpha release gives people an early preview of Snowstorm in read-only mode.
Just follow the setup guide and import a snapshot.

### Features
- Browsing concepts including all descriptions and relationships in one response.
- Concept search:
  - ECL 1.3 using inferred or stated form
  - Term filter using FSN
- Reference set member search.
