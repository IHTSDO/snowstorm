# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 10.9.1 Release (July 2025)
Maintenance release with bug fixes and general improvements.

### Features
- MAINT-2930 Implement the branch save listener

## 10.9.0 Release (July 2025)
Maintenance release with bug fixes and general improvements.

### Features
- MAINT-2845 Send Branch changes to queue
- RAP-160 Automatically download from S3 bucket to start importing CodeSystem version
- MAINT-2739 Disable creation of CNC Inactivation Indicators per branch

### Improvements
- VAL-485 Include inferred Relationships when validating Concept
- MAINT-2821 Add LOINC, NUVA and NPU CodeSystem configuration
- MAINT-2901 Improve pagination logic when saving large classification results
- PIP-854 Add Chile and Jamaica to CodeSystem configuration

### Fixes
- RAP-33 Rename ReferenceSet `AssociationReference` to `Association`
- MAINT-2024 Allow export with null `ReferenceSetMember.moduleId` field
- MAINT-2827 Allow import to complete when running locally
- MAINT-2865 Remove hardcoded limit size for `memberOf` ECL query
- MAINT-2870 Improve `memberOf` ECL query to not require `ReferencedConceptsLookup`
- MAINT-2853 Fix logging level when updating MDRS
- MAINT-2908 Fix the result window is too large issue when building concepts lookup during delta import

## 10.8.2 Release (May 2025)
Maintenance release with bug fixes and general improvements.

### Features
[ECL member of query performance enhancement](docs/ecl-memberOf-query-performance-enhancement.md)
- PIP-677 Add new index for reference set concept id lookup (concepts-lookup)
- PIP-680 Improve performance of "member of" ECL query
- PIP-681 Add admin function to rebuild and clear concepts-lookup
- PIP-679 Add to "concepts-lookup" during import
- PIP-678 Add to "concepts-lookup" during authoring
- PIP-700 Add configuration to optionally use "concepts-lookup" index during ECL queries
- PIP-710 Improve performance of compound ECL "member of" query
- PIP-772 Handle duplicate refset member inactivation when building/promoting concept lookups during authoring
- PIP-773 Improve ECL refinement expression with member of query

### Improvements
- MAINT-2628 Update Accept-Language header parser for language with regional dialect
- ISTO-130 Add CZ to default configuration
- PIP-662 Address CVE-2024-43709
- MAINT-2793 Use expectedExtensionModules when donating Concept
- PIP-730 Update ECL "member of" query with dot notation to use concepts-lookup
- MAINT-2545 Remove current MDRS automation
- MAINT-2543 Update MDRS when starting new authoring cycle 
- MAINT-2542 Update MDRS when versioning
- MAINT-2544 Update MDRS when upgrading
- MAINT-2686 Stop ability to create Descriptions with capitalized language  codes
- PIP-770 Address CVE-2025-31651

### Fixes
- ISTO-129 Suppress not found error when querying unknown resource in FHIR
- MAINT-2732 Prevent error during startup when CodeSystemVersion has no underlying Branch
- MAINT-2788 Stop published Annotations losing "released" and "effectiveTime" properties during rebase
- MAINT-2801 Fix en-gb import by specifying dependant CodeSystem
- MAINT-2761 Stop publishes Annotations being deleted
- 
## 10.7.0 Release (January 2025)
Maintenance release with bug fixes and general improvements.

### Improvements
- MAINT-2309 Prevent copy endpoint from modifying versioned Branch
- PIP-575 Address CVE-2024-38816
- PIP-584 Address CVE-2024-47072
- PIP-583 Address CVE-2024-51132

### Fixes
- ISTO-125 Allow pagination beyond 10K for LOINC
- ISTO-127 Improve handling of term search for LOINC

## 10.6.1 Release (December 2024)
Maintenance release with bug fix.

### Fixes
- MAINT-2699 Improve `additional-en-language-refset-delta` endpoint to support processing of duplicate ReferenceSetMembers.
- MAINT-2691 Add 'clone' method to Annotation class to improve handling of annotations during rebase.

## 10.5.0 Release (November 2024)
Maintenance release with improvements
### Improvements
- MAINT-2602 Improve logging when encountering exception during CodeSystem upgrade
- MAINT-2640 Improve logging when encountering exception interacting with Elasticsearch
- MAINT-2667 Add French Edition to default application properties
- MAINT-2464 Improve Snapshot import times

### Fixes
- MAINT-2665 Fix FHIR ValueSet expansion to search FSN & Synonyms, not Text Definitions
- MAINT-2308 Fix blank assertion group names to skip validation
- MAINT-2677 Fix bug where Semantic index was not updated when non-isa Relationships were inactivated

## 10.4.3 Release (October 2024)
Maintenance release with improvements
### Improvements
- MAINT-2679 Add refsets field to QueryConcept so that authoring platform can work with snowstorm-perf on the same semantic index

## 10.4.2 Release (July 2024)
Maintenance release with bug fixes and improvements.
### Fixes
- ISTO-119 Snowstorm MRCM search does not match translated terms
- VAL-384 Exclude the relationships from the concept before running Drools validation
- RAP-85 ModuleDependency automation not working as expected for Derivative releases
- MAINT-2586 ECL member of query results return only active concepts when enclosed in round brackets
- MAINT-2606 Fix ECL conjunction query search results with filters, supplements or member of queries
- MAINT-2609 Update ECL parser to parse query containing memberOf and other unary operators such as descendantOf correctly
- MAINT-2452 Snowstorm [fix]: prevent updates to published, inactive, malformed OWL axioms

### Improvements
- MAINT-2557 Adjust annotations filename and location to match expectations set in 20240601 International release
- MAINT-2472 Write unit test to prove Snowstorm handles duplicate language reference set entries correctly
- MAINT-2649 Allow RELEASE_LEAD to run "additional-en-language-refset-delta" operation

## 10.3.1 Release (May 2024)
Maintenance release with bug fixes and improvements.
### Fixes
- AMS-41 Update correct branch metadata item when upgrading CodeSystem
- MAINT-2529 Prevent startup from failing if MAIN branch is locked

### Improvements
- MAINT-2528 Improve log message when Traceability data is chunked 
- MAINT-2547 Allow configurable aggregation search size
- VAL-376 Add Drools assertion for German language
- VAL-377 Identify Relationships that are in a different module from the source Concept
- MAINT-2449 Upgrade HAPI-FHIR to 7.0.2
- MAINT-2550 Reduce excessive logging when classification job is timed out

## 10.2.1 Release (March 2024)
Maintenance release with bug fixes and improvements.
### Fixes
- MAINT-2476 Fix de-serialization bug when extra colon found in String concrete value
- MAINT-2490 Stop applying default module id to components if module has expected value

### Improvements
- ISTO-107 Support loading LOINC 2.73 and above
- MAINT-2493 Change annotation column from languageCode to languageDialectCode
- MAINT-2403 Add option to store Extensions in separate Semantic index
- MAINT-2496 Improve performance of integrity report by increasing page size

## 10.1.1 Hotfix Release (February 2024)

### Fixes
- MAINT-2502 Performance improvement for rebasing

## 10.1.0 Release (January 2024)
Maintenance release with bug fixes and improvements.

### Breaking
- Elasticsearch 8.11.1 onwards must be used with this release.

### Fixes
- MAINT-2418 Full import is no longer working due to reference set types update during start up
- ISTO-112 Fix swagger doc parameter names
- MAINT-2454 Snowstorm: FHIR config missing for Germany edition
- MAINT-2424 Update en-gb language refset copy api to avoid duplication and deletion
- VAL-273 Drools update: Duplicate FSNs

### Improvements
- MAINT-2456 Increase number of modules returned from CodeSystems endpoint
- MAINT-2361 Snowstorm: Move semantic tag list into configuration
- MAINT-2414 Snowstorm: Allow parsing of SubAnnotationPropertyOf
- MAINT-2441 Add config to switch off sending JMS topic message when creating versions during RF2 import
- MAINT-2457 Fix Snowstorm jenkins builds with intermittent test failures caused by index conflict during deleteAll
- PIP-369 Document Elasticsearch 7 to Elasticsearch 8 migration options
- PIP-458 Add abilities to configure shards externally after Elasticsearch 8 upgrade
- PIP-468 Upgrade Spring Boot to 3.2 and ElasticSearch to 8.11.1
- PIP-479 Upgrade activemq-broker to 6.0.1
- VAL-361 Drools: Add exclusions list

## 10.0.1 Release (December 2023)
Major release with Spring Boot 3 & Elasticsearch 8 upgrade.

### Breaking
- Elasticsearch 8 must be used with this release. Existing cluster must be upgraded to Elasticsearch 8.7.1 onwards before upgrading Snowstorm to 10.0.1.

### Improvements
- PIP-307 Upgrade to Elasticsearch 8
- PIP-374 Upgrade to Spring Boot 3

### Fixes
- MAINT-2384 Fix bug where an ECL query would fail if the modelling of a Concept had a concrete value with four or more decimal places.

## 9.2.0 Release (November 2023)
Maintenance release with Annotation improvements.

### Improvements
- MAINT-2370 Move the Annotation language code to a new column in RF2

### Fixes
- MAINT-2357 Remove any alternate identifiers that reference the concept while deleting a concept
- MAINT-2366 Delete alternate identifier indices when running the application with the delete-indices flag

## 9.0.0 Release (September 2023)
Major release with Java 17 upgrade.

### Breaking
- Java 17 must be used with this release.

### Features
- MAINT-2282 Add `POST multisearch/descriptions` endpoint for searching Descriptions across multiple CodeSystems

### Improvements
- MAINT-1038 Raise merge conflict when one branch has a deleted Concept and the other has a new Synonym on the same Concept
- MAINT-1930 Retrieve semantic tag based on language, with default being en
- PIP-289 Upgrade from Java 11 to Java 17
- MAINT-2199 Improve en-gb language refsets copying during IE and NZ extension upgrade to detect any existing preferred terms created in extension module

### Fixes
- ISTO-95 Block `FULL` RF2 imports unless request is on an empty `MAIN` branch
- PIP-267 Stop classification status becoming stuck when encountering a `NotFoundException`
- MAINT-2227 Fix bug preventing loading of all content in LOINC (2.72) package
- MAINT-2296 Stop throwing exception when no Concepts found when querying `concept-or-identifier-ref-concept` endpoint
- MAINT-2280 Do not record any Traceability Activities if an exception occurs
- MAINT-2310 Suppress `EC2MetadataUtils` warning message
- FRI-543 Stop duplicate Concept Not Current indicators from being generated when a CodeSystem upgrades its dependency

## 8.3.0 Release (August 2023)
Maintenance release with bug fixes and improvements.

### Features
- BROWSE-638 Add support for finding the referenced Component of an alternative Identifier.
- MAINT-2220 Add new `/identifier` endpoint for searching for Concepts by their alternative Identifiers.

### Improvements
- PIP-254 Change role group name from PROJECT_LEAD to PROJECT_LEAD.
- MAINT-2216 Enable MRCM Domain Templates to be updated during a rebase commit.
- MAINT-2188 Move MRCM Domain Templates into appropriate module when edited.
- MAINT-1988 Stop writing logs to redundant `tmp/spring.log` file.
- MAINT-2228 Add CodeSystem's Branch path to message when versioning.
- MAINT-2258 Send Classification status to a new response queue for authoring
- PIP-288 Fix vulnerabilities in guava, jackson-databind and tomcat-embed-core libraries.
- PIP-279 Explicitly add `drools-engine` and `drools-core-dynamic` as dependencies.

### Fixes
- MAINT-2192 Fix bug which resulted in modifying an inactive Component being audited as an 'inactivation'.
- FRI-526 Only update dependency package in Branch metadata during an Extension's upgrade routine.
- ISTO-94 Fix `active` flag in FHIR interface when expanding a ValueSet.
- ISTO-97 Fix bug which resulted in a blank moduleId when adding a Description to an inactive Concept.

## 8.2.2 Release (June 2023)
Maintenance release with bug fixes and improvements.

### Fixes
- MAINT-2222 Fix Daily Build Imports no longer working with spring-boot 2.7.12 .

### Improvements
- PIP-270 Upgrade tomcat-embed library from 9.0.73 to 9.0.74.


## 8.2.1 Release (May 2023)
This release improves the code system versioning process as well as upgrades project dependencies. 

### Features
- CRS-713 Send message to JMS topic when code system has been versioned.

### Improvements
- PIP-268 Upgrade spring-boot to 2.7.12.

## 8.2.0 Release (May 2023)
This release adds the ability to load, persist and search the RF2 Identifier file (e.g. sct2_Identifier_Snapshot_INT_20230509.txt). 
As well as this, several critical security vulnerabilities have been fixed with a mechanism implemented to fix any future 
critical security vulnerabilities more easily. Other general bug fixes and improvements have also been made.

### Features
- MAINT-2144 Support loading, persisting and searching of the RF2 Identifier file (sct2_Identifier_Snapshot_INT_20230509.txt).

### Improvements
- MAINT-1984 Rename content promotion endpoint from `concepts/donate` to `concepts/copy`.
- PIP-215 Change logging of ECL statement from `info` to `debug` when finding Concept identifiers.
- MAINT-2106 Improve IntegrityIssueReport returned from API when error occurs during merging.
- MAINT-2145 Return Concept's alternative Identifier, if available, from API response.
- PIP-248 Fix several critical security vulnerabilities.
- MAINT-2166 Allow users with `PROJECT_LEAD` permission to upgrade a CodeSystem.
- MAINT-2165 Add endpoint for retrieving Branch metadata (`/branches/{branch}/metadata`).

### Fixes
- FRI-637 Stop rebasing from crashing when too many Branches are in scope of query.
- MAINT-2120 Stop duplicate Elasticsearch documents being created when rebasing the same Description inactivation.
 
## 8.1.2 Release (March 2023)
This release has an overhaul of the FHIR API :fire: and adds support for multiple code systems within the FHIR API!

Previously Snowstorm only supported SNOMED CT but this release adds support for LOINC, ICD-10 (International), ICD-10-CM (US) and
any other code system using a FHIR package or the Custom code system format.

The custom code system format allows any local or national code system to be supported. These need to be transformed into the custom code system format before being loaded.

See [Snowstorm FHIR Terminology Server documentation](https://github.com/IHTSDO/snowstorm/blob/master/docs/using-the-fhir-api.md) for details of how to 
load each package format and a link to the updated Postman collection for v8.

### Breaking
- FHIR API no longer defaults to the SNOMED CT code system, now a system must always be specified. This is in alignment with the FHIR specification.
- Renamed FHIR Elasticsearch indices with `fhir_` prefix

### Features
- Add FHIR support generic CodeSystems
  - HL7 terminologies and many other code systems supported
  - Import can use 'FHIR package' from simplifier.net
  - Alternatively import can use HAPI custom code system format
- Add FHIR Support for LOINC
  - Import uses LOINC package format
- Add FHIR Support for ICD-10
  - Import uses ICD-10 ClaML format
- Add FHIR Support for ICD-10-CM
  - Import uses ICD-10-CM native format
- Add support for FHIR ValueSets with nesting, for non-SNOMED CT value sets

### Improvements
- Improve FHIR code system version resolution
- Include resolved code system version in FHIR responses
- Improved FHIR error catching and reporting
- ISTO-41 Include optional non-SNOMED CT term in ReferenceSetMembers API response 
- FRI-491 Improve `autoMergedConcept` in `MergeReviewConceptVersions` to be based on `sourceConcept` with changes from `targetConcept` re-applied 
- FRI-600 Add property to `MergeReviewConceptVersions` to indicate whether `targetConcept` is based on an older version of the CodeSystem in relation to `sourceConcept`

### Fixes
- Various fixes for FHIR specification conformance
- MAINT-2072 Fix reversion of Historical Association ReferenceSetMembers
- FRI-492 Stop setting `targetConcept` in `MergeReviewConceptVersions` to have the same state as the latest versioned content
- ISTO-57 Fix pagination of CSV download
- ISTO-67 Fix bug which prevented ECL statements being converted to JSON when containing `minus`

## 8.0.0 Release (February 2023)
Major release with Elasticsearch 7.10.0 upgrade.

### Breaking
- Elasticsearch from 7.10.0 to 7.17.9 must be used with this release.

### Improvements
- General
  - MAINT-2018 Update Snowstorm to use Elasticsearch client 7.10.0.
  - FRI-567 Add check to CodeSystem upgrade to prevent multiple sequential upgrades.
  - ISTO-54 Add active flag to the Relationship target within the Concept JSON representation.
  - FRI-398 Force branch paths to be in upper case.
  - FRI-592 Update `generateAdditionalLanguageRefsetDelta` endpoint to work with both monthly and 6-monthly upgrades.
- Authoring
  - FRI-550 Add functionality to support CodeSystem upgrades via authoring platform UI.

### Fixes
- MAINT-2095 Fix browser performance search test case for ECL.
- MAINT-2081 Prevent classification statuses from becoming stuck by listening to JMS Topic instead of Queue (requires Classification Service v7.0.0).


## 7.12.0 Release (December 2022)
Maintenance release with bug fixes and improvements.
### Improvements
- General
  - MAINT-1974 Add optional configuration to allow new ReferenceSet types to be configured on branch other than MAIN.
  
### Fixes
- MAINT-2071 Remove communication with Authoring Acceptance Gateway when completing classifications.
- MAINT-1940 Stop inactive historical associations switching module when rebasing.
- FRI-565 Correct Extension's traceability log when performing rebase after upgrading dependency.


## 7.11.0 Release (October 2022)
Maintenance release with bug fixes and improvements.
### Improvements
- General
  - MAINT-2037 Add `descriptionIds` as optional request parameter to `/{branch}/descriptions` endpoint
  - MAINT-1439 Add `/user-group/{userGroup}` endpoint for retrieving permissions associated with User Group
  - FRI-443 Send large Traceability JMS messages in batches
  - MAINT-2044 Remove unused and commented-out code in WebConfig
  - MAINT-2052 Revert re-used ReferenceSetMember if no longer required
- Authoring
  - FRI-480 Add new rule for ensuring defined Concepts must have at least one defining Relationship

### Fixes
- MAINT-1961 Fix `/browser/{branch}/concepts/bulk` endpoint from hanging if Branch doesn't exist
- MAINT-2036 Fix adding multiple, new historical associations to an inactive Concept from failing
- BROWSE-461 Fix parsing of ECL statements with nested `Parent of` and `Or` operations
- MAINT-1969 Fix published components from losing their released flags during rebase
- MAINT-1862 Fix `searchAfter` request parameter to work with ECL dot notation
- MAINT-2051 Fix the changing of a Concept's inactivation reason from resulting in duplicate ReferenceSetMembers


## 7.10.0 Release (September 2022)
Maintenance release with integration support for [Consul](https://www.consul.io/) and [Vault](https://www.vaultproject.io/)
### Features
  - PIP-181 Add integration support for [Consul](https://www.consul.io/) and [Vault](https://www.vaultproject.io/). 
    See [spring-cloud-consul](https://cloud.spring.io/spring-cloud-consul/reference/html/) and [spring-vault](https://cloud.spring.io/spring-cloud-vault/reference/html/) for more information about setups and configuration.
  - BROWSE-427 Add support for resolution of version specific URIs.

### Improvements
 - General
   - MAINT-1921 Upgrade Spring Boot to 2.7.0 and migrate swagger-ui to springdoc-openapi from SpringFox.
   - PIP-182 Improve Snowstorm communication with `Authoring Acceptance Gateway` (AAG) service during start-up.
   - FRI-475 Update security config to check roles required when `ims-security.roles.enabled` is true.
 - Authoring 
   - MAINT-1993 Update drools rules to check active GCIs only.
   - MAINT-2032 Remove old redundant ECL parser generation files.
   - BROWSE-506 Use correct branch in browser for version-relative URIs.
   - FRI-474 Refactor traceability log service to handle superseded changes during rebase.
   - FRI-471 Allow `ADMIN` and `MANAGER` roles to update code system versions.
   - FRI-490 Extend import APIs to allow user to specify module ids.

### Fixes
  - ISTO-42 Fix ECL caching bug.
  - MAINT-1743 Fix loss of effective time in components saved during rebase.
  - MAINT-1999 Avoid persisting refset members unnecessarily when saving concepts.
  - MAINT-2003 Correct default language refset configuration for `es`


## 7.9.3 Release (June 2022)
### Breaking
  - ISTO-16 Add support for ECL 2.0. Snowstorm now requires all ECL queries to be valid according to ECL 2.0 specification. 
    See [SNOMED CT Expression Constraint Language](https://github.com/IHTSDO/snomed-expression-constraint-language) for more details about syntax and examples.
### Features
  - MAINT-1644 Concepts donation via ECL.

### Improvements
  - MAINT-1960 Prevent SCTID cache setup for new partition/namespace if original request for identifiers fails.
  - MAINT-1945 Updated logic of DescriptionDroolsValidationService when finding the parents that not containing a specific semantic tag.
  - ISTO-32 Separate test indices for FHIR. This avoids branch already exists error when the FHIR test context does not tear down fast enough.
  - FRI-460 Only returns versions of a code system when the date of release is after the current date.
  - FRI-123 Add CommitServiceHookClient to ClassificationService

### Fixes
  - MAINT-1968 Fix code quality warnings and remove unused variables reported by LGTM.
  - MAINT-1991 Delete temp file after RF2 import is done to avoid concurrency issue.
  - FRI-444 Log components from the parent branch in traceability activities when changes on task are discarded during rebase.


## 7.8.0 Release (May 2022)
Maintenance release with bug fixes and improvements.

### Fixes
  - Fix 379 Set default Language Reference Set for a given language
  - Fix 388 Update security-configuration.md documentation

### Improvements
  - FRI-424 Simplify `new-authoring-cycle` endpoint by removing redundant request parameters
  - FRI-410 Add support for circular dependencies and empty modules in MDRS generation
  - MAINT-1918 Fix export of ICD-10 to appear in ExtendedMap instead of ComplexMap 
  - FRI-210 Improve concept merge screen to preserve International changes after Extension upgrade
  - MAINT-1911 Add Description inactivation reason "GRAMMATICAL_DESCRIPTION_ERROR"
  - MAINT-1828 Update version of snomed-owl-toolkit to 3.0.7
  - MAINT-1841 Set transient stated Relationships to have same moduleId as parent axiom
  - FRI-404 Only return versions of a CodeSystem when the date of release is after the current date
  - FRI-414 Return metadata related to the Descriptor Reference Set when saving/updating a Concept
  - MAINT-1902 Retain Admin user groups after restarting service
  - MAINT-1807 Make Relationships move module when inactivated in an Extension  
  - FRI-370 Add endpoint for retrieving all semantic tags
  - MAINT-1003 Reuse existing inactivation/association ReferenceSetMembers when updating metadata

## 7.7.0 Release (Feb 2022)

### Breaking
- General
  - When using `returnIdOnly` parameter; the ids returned now use 'string' rather than 'number' JSON data type, to avoid rounding. Fixes #367.

### Improvements
  - General
    - Add `searchAfter` request parameter to `/browser/{branch}/members` endpoint for querying 10K+ records.
    - `latestDailyBuild` property added to `CodeSystem`
  - Authoring
    - FRI-234 Prevent versioned content being deleted when performing rebase.
    - FRI-365 When Concepts are re-activated in an extension module, various historical associations and inactivation indicators are inactivated in the same extension module.
    - MAINT-1871 Fix bug in IntegrityService to handle branch paths with more than 4 levels.
    - FRI-393 Simple additional module now correctly assumes it is dependent on Core Module.
    - FRI-388 Use existing responseMessageStatusQueue for classification status.
    - MAINT-1882 Exclude MRCM reference sets when importing IPS terminology.
    - MAINT-1883 Exclude Module Dependency reference set in exported archive when `unpromotedChangesOnly` flag is `true`.
     
## 7.6.0 Release (Jan 2022)
Minor release with bug fixes and improvements.

### Features
- Authoring
  - FRI-27 Retrieve classification status via JMS optionally. To enable this you need to set `classification-service.job.status.use-jms=true`
  and add the queue name in `classification-service.message.status.destination` parameter. It works with classification service 6.1.0 onwards.
  - MAINT-1791 Add new inactivation reasons: `Classification Derived Concept` and `Meaning of Concept Unknown`.
  - MAINT-1792 Add new historical associations: `PARTIALLY_EQUIVALENT_TO` and `POSSIBLY_REPLACED_BY`.

### Improvements
 - Authoring
   - FRI-341 Exclude components updated by parent branch when logging rebase changes in traceability service.
   - MAINT-1843 Update finding Top Level Hierarchies logic.
   - VAL-231 Update logic of DescriptionDroolsValidationService when fetching FSNs with language reference sets.
 - General
   - Bump log4j-api from 2.15.0 to 2.17.0 for security updates.
   - FRI-353 Add `isNullEffectiveTime` to refset member search api for unpublished components only.

### Fixes
 - Fixes #358 ECL-cache bug fix when combining ecl and conceptIds parameters.
 - Fixes #352 Fix daily build rollback error when large number(over 100MB) of version replaced documents found.
 - Authoring
   - MAINT-1842 Fix false warnings when using semantic tag pattern.

## 7.5.4 Release (Dec 2021)
Maintenance release with new features in FHIR and many improvements and fixes.

### Breaking
- Authoring
  - MAINT-1824 Disable traceability by default.

### Features
- FHIR
  - Designation use context FHIR extension implementation.
- General
  - BROWSE-429 Add URI dereference support for module and version specific URIs.
  - BROWSE-470 Implement ECL cache and add cache stats endpoint. To disable cache update config key `cache.ecl.enabled` to false.
- Authoring
  - FRI-299 Add POST version of member search to allow for bulk item filtering.
  - FRI-73 Include MDR generation when versioning content. Add MDR Preview API, cache of international modules and logic for how high up MD tree to populate.

### Improvements
- General
  - MAINT-1746 Concepts deleted on both sides of merge do not conflict but concepts deleted on one side of the merge still conflict.
  - MAINT-1790 Allow pagination beyond 10K reference set members.
  - MAINT-1811 Support single character search for Korean.
  - MAINT-1825 Refactor code system and version cache.
  - MAINT-1827 Make code system dependant version cache thread safe.
  - MAINT-1834 Don't output unchanged MDRS rows when exporting Delta.
- Authoring
  - FRI-121 Add date filter to code system versioning traceability backfill.
  - FRI-127 Move setAuthorFlag logic from controller to service and set batch-change flag when importing.
  - FRI-262 Update descriptor refset when creating/deleting refset to prevent duplicates. Refactor metadata for importing code system version.
  - FRI-283 Copy release info from parent branch when merge conflict.
  - FRI-319 Reliable deduplication of historical indicators and associations. Remove incorrect inactivation indicator member collection check.
  - MAINT-1722 Improve on-save validation performance with drools query and branch criteria cache.
  - MAINT-1725 Order is-a relationships top when no config for a hierarchy.
  - MAINT-1728 Stop writing semantic updates when no logical change.

### Fixes
- Fixes #347 Log4j vulnerability by upgrading minor versions to latest.
- Fix #264 / MAINT-1664 Async axiom conversion error handling.
- Fixes #339 importing RF2 from local file bug.
- FHIR
  - Fix #322 / MAINT-1779 FHIR: default dialect and no inactive terms.
- Authoring
  - MAINT-1719 Fixed NPE when constructing concrete values.
  - MAINT-1722 Val performance: fix query cache key and tweak ECL string.
  - MAINT-1758 Fix restoration of donated content when rolling back upgrade.
  - MAINT-1810 Fix deletion of redundant orphan relationships.
 
 
## 7.4.0 Release (Oct 2021)
Minor release with bug fixes and improvements.

### Features
- BROWSE-492 Restrict search term length. Defaults to min of 3, max of 250. Config item `search.term.maximumLength`.
- Authoring
  - FRI-182 Added method for author set flags on a branch `POST /branches/{branchPath}/actions/set-author-flag`
  - FRI-145 Added convenience method to set previous package details for new authoring cycle `POST /codesystems/{shortName}/new-authoring-cycle`
### Improvements
- BROWSE-495 Refactor concept history function for better performance.
- MAINT-1769 Add branch metadata upsert endpoint.
- Documentation
  - Minor improvements to getting stated guide.
- Authoring
  - FRI-256 Log traceability when content is versioned.
  - FRI-121 Add admin traceability backfil function for v3.1 upgrade. 
### Fixes
- Fix #323 / MAINT-1734 Remove concept 'concrete' property from various API responses.
- FHIR
  - Fix #319 allow double encoded ECL in implicit FHIR value sets.
- Authoring
  - MAINT-1747 Fix content rollback of failed promotion commits.
  - FRI-248 Prevent permanent branch lock after service commit hooks blocks promotion.
  - FRI-253 Manually merged concepts logged as update in traceability rather than create.


## 7.3.0 Release (Sept 2021)
Minor release to implement the `internalRelease` flag on code systems. This allows Snomed International to hide the first 
few internal monthly releases of the International Edition from the public browser. Monthly public releases of the International Edition will be available starting Feburary 2022.

### Features
- FRI-183 Add `internalRelease` flag to CodeSystemVersion. By default this is hidden from the listing by default, this is configurable. 

### Improvements
- FRI-166 Upgrade Snomed Drools engine to 3.0.3.
- Implement code quality improvements suggested by LGTM.com .

### Fixes
- FRI-245 Fix traceability bug where component merge duplicate resolution was logged as deletion.
- MAINT-1723 Fix MRCM attribute listing; only list applicable ranges under each attribute.
- Improve admin restore-release-status function to be triggered by missing effectiveTime.


## 7.2.0 Release (Sept 2021)
Feature release in preparation for frequent SNOMED CT releases in the Authoring Platform. Authoring traceability service integration has been rewritten using a simpler message format. This release also contains an important fix to preserve the semantic index of the existing release branch when upgrading a code system.

### Breaking
- Authoring
  - FRI-66 Authoring traceability messaging format has been simplified. This change requires upgrading Authoring Traceability Service to version 3.x.

### Features
- Authoring
  - FRI-54 Authoring Acceptance Gateway can block branch promotion.
  - FRI-160 Added report to list new descriptions with option to list only those not yet promoted.

### Improvements
- FHIR
  - MAINT-1686 Support title and sorting in CodeSystem endpoint.
- General
  - FRI-158 Add effectiveTime and isReleased filters to concept search.
  - BROWSE-463 Log ERROR with details if duplicate document found.
- Authoring
  - FRI-160 Report to list new descriptions with option to list only those not yet promoted.
  - MAINT-1710 Allow release patch import using delta or snapshot.

### Fixes
- BROWSE-464 Fix to preserve the semantic index of the existing release branch when upgrading a code system. This prevents creating a data issue related to duplicate concept errors.
- Authoring
  - MAINT-1696 Fix Snowstorm restore release status admin function.


## 7.1.2 Release (July 2021)
Maintenance release with a couple of new features and many improvements and fixes.

### Breaking
- Admin function to restore release flags now accepts less parameters because the dependant release branch is now looked up automatically.

### Features
- Authoring
  - FRI-60 Maintain classification status flag for all branches when authoring. This prevents the need to classify description changes.
  - FRI-61 Add service hook to send commit information to Authoring Acceptance Gateway or other service.

### Improvements
- BROWSE-415 Update default search config for Spanish language, all characters folded.
  - Admin function to reindex descriptions must be used to apply config change to existing data.
- MAINT-1671 Add Code System "owner" field for display purposes. Added API function to set all owners, from all known defaults in config.
- MAINT-1649 Add module filter to aggregated refset member search.
- MAINT-1268 Show dependant version effectiveTime when viewing a CodeSystem.
- RT2-56 Add refset filter to RF2 export.
- RP-475 Add bulk feature for finding refset members
- MAINT-1657 allow "Accept Language" header formats with more than two characters in the dialect.
- FHIR
  - MAINT-1663 Add support for version element in ValueSet compose.
  - MAINT-1354 Add support for FHIR to return results past 10K.
  - Fix #285 Allow disjunction in FHIR filters. Also correct expansion item count and warp when skipping forward pages
  - MAINT-1575 Documentation: Clarify use of xsct in ValueSet expansion url parameter.
- Authoring
  - MAINT-1667 Add admin function to clean up inactive inferred relationships during an authoring cycle.
  - FRI-101 Drools validation failures now include unique ruleId, to be used in whitelisting.

### Fixes
- Fix #278 BROWSE-436 Allow whitespace in Accept-Language HTTP header.
- FHIR
  - MAINT-1660 Fix regression for CodeSystem operations on unpublished content via http://snomed.info/xsct.
- Authoring
  - MAINT-1416 Do not apply default module if record matches previously released version.
  - MAINT-1642 Fix partial commit rollback function.
  - MAINT-1666 Fix release flag restore when there is no dependant release.
  - FRI-128 Consistently keep user security-context whenever switching to a new thread.
  - MAINT-1692 Refactored integrity-issue branch metadata flag for extension upgrade.


## 7.0.1 Release (May 2021) - Major Release for Java 11
Major release with Java 11 upgrade.

### Breaking
- Java 11 is now required to run Snowstorm

### Features
- BROWSE-218 Concept URI routing to correct CodeSystem and branch, configurable URL for any webapp. See [Web Router Configuration](docs/webrouter-configuration.md).
- Include the SNOMED CT Browser in Docker image.

### Improvements
- PIP-62 Java 11 is used to take advantage of the latest language features and avoid Java 8 End of Life.
- Dockerfile now compiles the application.
- BROWSE-409 Make ECL utility POST methods accessible when in read-only mode.
- FHIR
  - #199 MAINT-1567 Allow dialect to be specified in displayLanguage and designation parameters.
  - MAINT-1030 Add Concept active state to CodeSystem `$lookup` and ValueSet `$expand` operation responses.
- Authoring
  - MAINT-1618 When creating refset members via the API if no module is given the `defaultModuleId` from branch metadata will be used. Branch metadata is inherited across branches but not Code Systems.
  - MAINT-1616 RF2 export entry paths now include export type `Delta`, `Snapshot` or `Full`. Filenames include 2 digit country code from CodeSystem entry rather than 'INT'. This new behaviour can be suppressed with `legacyZipNaming` flag.
  - MAINT-386 Include changes to preferred terms in branch rebase conflict detection.
  - MAINT-1572 Improve extension concept save by prefetching SCTIDs from CIS for namespaces other than International.

### Fixes
- Fix #259 MAINT-1653 Failed RF2 import triggers commit rollback.
- MAINT-1372 Validate requests to `/browser/{branch}/concepts/bulk-load` to prevent "No value specified for terms query" Elasticsearch error.
- MAINT-1621 Fix creation of RBAC permission records when using `global` flag.
- CDI-107 In ECL prevent concrete values matching conceptIds and vice versa.
- Fix AWS signing with empty request entity.
- Authoring
  - MAINT-1624 Apply attribute sorting to classification save concept preview.
  - MAINT-1628 Squash duplicate language refset members caused by branch merge.


## 6.2.1 Fix Release (March 2021) - Latest semantic index bug fixes
Minor fix release for semantic index bug fixes and improvements.

### Improvements
- MAINT-1623 Only replace incorrect documents during whole semantic-index rebuild on MAIN.

### Fixes
- BROWSE-411 Extension semantic index update must use extension dependant version of MAIN as base. Previously the latest version of MAIN was always used.


## 6.2.0 Release (March 2021) - Concrete domain authoring support and semantic update fix
This release adds authoring support for concepts using concrete domains including updates to the MRCM endpoints.

This release also includes an important fix to the semantic index update process. Before this fix fictitious transitive closure loops could appear in the semantic index after
upgrading an extension, which caused inconsistent behaviour in hierarchy browsing and ECL results. The admin rebuild semantic index function can be used to heal existing
indexes with this problem.

### Breaking
The following minor breaking changes were made in this maintenance release.
- Removed deprecated code-system 'migrate' function in favour of 'upgrade' function. The migrate function was never implemented completely and has been deprecated for over a year.
- Util function GET `/util/parse-ecl` changed to POST and moved to `/util/ecl-string-to-model` for consistent naming with new function `/util/ecl-model-to-string`.

### Features
- FHIR
  - Add support for `http://snomed.info/xsct` URI to access unversioned content from a code system working branch.
- New Concept history endpoint to list code system versions in which a concept or its components were changed.
  (Requires many code system versions to be imported).
- Add API to convert ECL JSON language model back to ECL string. To support ECL builder frontend implementations.
- Authoring
  - Concrete domain attributes and ranges listed by MRCM endpoints
  - Concrete domains Drools validation implementation.
  - Concept save / update with concrete domains values in stated form.

### Improvements
- New `module` filter in concept search endpoint. Accepts set of concept identifiers.
- Role Based Access Control
  - Add user roles to codesystem listing.
  - Add user global-roles to single branch response.
- Admin commit-rollback function now automatically removes any code-system version created by the commit.
- Authoring
  - New `validate` flag in concept save and update function. Provides the ability to combine Drools validation and save functions into a single API call
    for a cleaner design and slight performance improvement. _New flag defaults to false so as not to break existing implementations._
  - Integrity check now includes attribute type concepts from concrete attributes.

### Fixes
- Fix semantic index update function:
  - Rebase/upgrade no longer causes fictitious semantic index loops.
  - Include conflicting manually merged concepts in semantic update.
- Fix #207 ignore ECL characters in HTTP params for Tomcat 9.
- Fix group handling in ECL util function.
- FHIR
  - Fix documentation example for value-set expand with filter.
- Authoring
  - Improve conflicting concept merge behaviour so component release details can never be lost. Prevents delta exports of previously versioned content.
  - Make classification save function fail gracefully when semantic loop found.
  - Better handling of language refset members to remove duplicates during branch merging.
  - Fix regression: classification changes not logged in traceability.
  - On extension upgrade duplicate component removal use effective time to pick latest inferred relationships rather than version control dates.


## 6.0.3 Release (February 2021) - Support for concrete domains technical preview
Added support for concrete domains to host the International Edition Technical Preview.
Additions have been made to the concept browser representation to represent concrete relationship values and types but changes are backward compatible.
Full support for authoring concrete domains is still in development and will be released in the next few months.   
Note: All concrete domains features rely on having concrete domain content loaded.

### Features
- Concept browser format updated to include `concreteValue` with `value` and `dataType` for concrete fragments within axioms and concrete inferred relationships.
- Ability to import concrete domains from the proposed `sct2_RelationshipConcreteValues_Delta_...txt` RF2 file.
- Ability to export concrete domains in proposed RF2 format.
- ECL support for concrete domains, see [ECL refinements with concrete values](https://confluence.ihtsdotools.org/display/DOCECL/6.2+Refinements#id-6.2Refinements-ConcreteValues).
- Concept history list (beta).
- FHIR:
  - Add FHIR support for POST operation on ValueSet Expansion.
  - Add FHIR support for concrete values when normalForm property requested.
- Authoring:
  - Classification of concrete domain content.
  - Delta RF2 imports logged against Traceability Service (if feature is enabled).
  - Fixes #217 Option to export just changed components from a task branch using `unpromotedChangesOnly`.

### Improvements
- #219 Expose port 8080 in Docker container
- Add description type filtering to concept search.
- MRCM Template generation for concrete domains.
- FHIR:
  - Fix `$lookup` operation `displayLanguage` parameter.
  - Check all CodeSystem instances in `$lookup` when version not specified.
  - Prevent `CodeSystem` in a FHIR `coding` parameter from including module or version information.
  - Add support for _xsct_ URI `http://snomed.info/xsct` to indicate unversioned content.
- Authoring:
  - Prevent replacment of any existing description inactivation when concept made inactive.
  - Prevent duplicate inactivation indicators and historical associations by reusing existing inactive and improving branch merging.
  - Register SCTIDs with CIS in smaller batches, defaults to 1K, configurable.
- Log ES host and index prefix on startup as INFO.
- Log ClientAbortException as INFO without stack trace rather than ERROR

### Fixes
- #218 Remove repeated jar arg from Kubernetes config


## 5.1.0 Release - 2020-11-16 - Minor Improvements and Fixes

### Features
- MAINT-1492 Add API to create and start a server-local file import in a single call.
- MRCMMT-253 Add API to return all MRCM attributes as hierarchical structure.
- MAINT-1455 Add API to convert ECL string to JSON language model. To support ECL builder frontend implementations.

### Improvements
- #178 MAINT-1509 Improve export API Swagger documentation.
- MAINT-1502 Include descriptions as `referencedComponent` in listing of language reference set members API.
- API: New reference set members default active=true.
- API returns location header when creating concepts or refset members.
- MAINT-1413 Clear code system cache using release fix merge function.
- MAINT-1467 Performance fix: remove redundant versions replaced during branch promotion.
- MAINT-1473 Authoring: More detail in transitive closure loop error message.
- MAINT-1488 Log seconds taken by remote classification and overall.
- Improve unit tests: phase out use of stated relationships in favour of inferred relationships and axioms.
- Maven Pom: Remove old Nexus repository which has been retired.

### Fixes
- MAINT-1378 Extension maintenance: stop deleting unpublished extension descriptions when concept becomes inactive during International upgrade.
- MAINT-1498 Fix sorting of active and inactive axioms on same concept with same model.
- Fix utility class CommitExplorer after ES7 upgrade.
- MAINT-1503 Make export unit test stable; don't expect specific row order.


## 5.0.8 Release - 2020-10-30 - Fix release

### Improvements
- Improve Docker support and documentation.

### Fixes
- Fix concept search total count when using active filter.
- MAINT-1487 Remove redundant fields stored in the Relationship index.
- MAINT-1458 Authoring version control: commit rollback bug fix.
- MAINT-1501 Semantic index bug: parent versions not restored during rebase since ES7.


## 5.0.6 Release - 2020-10-16 - Fixes and Improvements

### Features
- New description search mode for whole words in addition to the word prefix and regex modes.
- #145 ECL query concept validation. If any concept in the query is not present and active on the branch 400 (Bad Request) is returned. Thank you to @jbarcas for this.
- #142 Allow description bulk fetch by conceptId. Thanks again to @jbarcas !

### Improvements
- Add extension preferred terms to concept TSV download.
- Docker documentation improvements.

### Fixes
- Many pagination and search results totals fixes including #161
- #132 Fix FHIR Medication response.
- #164 Fix docker config. Don't expose Elasticsearch port.
- #167 Enforce shutdown when `--exit` flag used.
- Admin function to clean up partial commit and unlock branch.


## 5.0.2 Release - 2020-09-22 - Major Release for Elasticsearch 7
New major release to support Elasticsearch 7 because Elasticsearch 6 is due to reach End Of Life.

Upgrade hints: Snapshot backup from Elasticsearch 6 can be restored into an Elasticsearch 7 cluster.
See [Elasticsearch upgrading documentation](https://www.elastic.co/guide/en/cloud/current/ec-upgrading-v7.html) for further information.

### Breaking
- Elasticsearch 7.6.x or greater MUST be used with this release.

### Features
- Support for Elasticsearch 7.x

### Improvements
- New section in Extension setup documentation for SNOMED Identifier Generation.
- Handle Elasticsearch timeout exception gracefully during content commit.

### Fixes
- Minor fixes to FHIR documentation links.

## 4.13.0 Release - 2020-08-11 - Minor Improvement Release

Minor improvement release before the next major release with Elasticsearch 7 support.

### Improvements
- Implement #135 - Option to return just concept identifiers in ECL results for ~5x performance improvement for large queries. Use `returnIdOnly` in request to `GET /{branch}/concepts`.


## 4.12.1 Release - 2020-07-16 - Role Based Access Control

Role based access control has been applied to the API to restrict which users can perform administration and authoring functions when not in read-only mode.  
See [Security Configuration Guide](docs/security-configuration.md).

### Features
- Role based access control
  - Roles can be assigned at global or branch level to user groups via the admin API.
  - Extendable solution allows granting branch permission to user groups using any role name. Useful for complex user interfaces.
  - List of roles displayed on each branch for the current user.
  - Roles `ADMIN` and `AUTHOR` applied to relevant API functions.
  - Careful cache design mitigates any RBAC performance impact.  

### Improvements
- Improved logging for axiom expression parsing errors.
- Add optional code system `maintainerType` field to aid extension categorisation and filtering in UIs.
- Add code system listing `forBranch` parameter which allows code system lookup using any ancestor branch.

#### FHIR API Improvements
- [FHIR documentation](docs/using-the-fhir-api.md) split into individual markdown files for each resource and operation
- The ValueSet $expand operation will now return an error if a request is made using the 'version' parameter.  This parameter is not supported by this operation and the error message will direct the user to use 'system-version' or 'force-system-version' instead.

### Fixes
- Add flag to opt in to content automations when upgrading extensions (only required in authoring environments).
- Authentication session memory leak fixed from previous RBAC solution.
- Fix for release-fix-branch promotion function when deletions are the only change.
- Allow batch job status to be access immediately after creation.

#### FHIR API Fixes
- The ConceptMap $translate operation now correctly determines which on which branch to look up the specific map, based on an (optional) full URI in the url parameter.


## 4.11.0 Release - Extension Authoring Upgrade Automations and UI Support

Features have been added to automate aspects of extension maintenance when upgrading to a new International release.  
See "Upgrading to a new International Release" in [Extension Authoring](docs/extension-authoring.md).

### Features
- Extension authoring upgrade support.
  - Enhanced branch integrity check which includes source concept and can be combined with a fix branch during the upgrade process.
  - Automatic integrity check and marking of branch with issues during upgrade process and fix promotions.
  - Automatic inactivation of language refset members which reference inactive descriptions.
  - Automatic inactivation of additional axioms which belong to an inactive concept.
  - Automatic addition of concept not current indicators for descriptions which reference inactive concepts.
  - Historic associations can be fetched from API to support update of existing axioms via UI.

### Improvements
- Fix #107 Add multi-module parameter to description search API.
- Allow concept bulk fetch by descriptionId.
- Allow versioned concepts to be deleted using the `force` flag.

#### FHIR API Improvements
- CodeSystem $lookup operation now supports 'property=*' to indicate that all known properties should be returned.
- When ValueSet $validate-code operation is called specifying a refset that either does not exist or contains no active members, an enhanced error message will now alert the user to this situation.
- ConceptMap $translate now allows inactivation indicators to be recovered.

### Fixes
- Allow batch job status to be access immediately after creation.
- Fix concept search when filtering by single conceptId.
- Prevent duplicate semantic index entries by adding hashcode and equals methods.
- Update UK extension module to `83821000000107`, thanks @lawley.

#### FHIR API Fixes
- Fix for Null Pointer Exception when source or target is omitted in a ConceptMap $translate operation


## 4.10.2 Release - 2020-05-27

This release features many new capabilities and improvements to the FHIR API as well as some other general improvements and minor fixes.

### Features
- FHIR
  - Add TerminologyCapabilities endpoint.
  - Implement list all ValueSets.
  - Implement search ValueSets.
  - Add support for validate-code operation in CodeSystem.
  - Add support for validate-code operation in ValueSet.
  - Add support for subsumes operation.
  - Add SCRUD support for StructureDefinition Resources.
- Authoring
  - MRCM Maintenance - Automatic generation of MRCM domain templates, triggered by changes to the MRCM refsets.
  - Function to generate language refset changes for extensions that base their language refset on an international one (for example IE or NZ).
### Improvements
- RF2 import skips empty lines
- Configuration
  - New flag to enable CIS ID registration process. The out-of-the-box setup uses an internal id generator so ID registration is now disabled by default.
  - Switch Canadian English codesystem shortname to SNOMEDCT-CA.
- FHIR
  - Make JSON the default response format when common browser headers detected, (format=json removed from examples).
  - Fix #114 Allow use of version 'UNVERSIONED' to indicate unpublished content should be used. By using the code system / daily build branch rather than a version branch.
  - Update HAPI library to 4.2.0.
  - FHIR Base URL to respond with simple webpage rather than metadata redirect.
  - CodeSystem $lookup uses PT rather than FSN.
  - ValueSet supports all specified search parameters and text modes where possible.
  - Add support for specifying FHIR system-version when expanding a ValueSet.
  - Add support for specifying language refset SCTID as designation in a ValueSet expansion.
  - Add support for en-x-NNNNNN in language headers.
  - Add support for ICD-0 map translations.
  - Add support for VS expansion force-system-version parameter.
  - Attempt expansion of ValueSet prior to creation.
- Snowstorm API
  - Fix #107 Add multi-module parameter to description search API.
  - Bulk load concepts by description id.
- Authoring
  - Add API for bulk refset member deletion.
  - Add API for bulk relationship deletion.
- Admin Functions
  - Function to restore released flag and associated fields of concept and related components.
### Fixes
- Branch child listing pagination.
- Search
  - Fix #106 Allow concept search by ECL filtered by conceptId list.
  - Fix MRCM attribute range search, use inferred form not stated.
- FHIR
  - Fix #112 Missing version when expanding stored valueset caused failure to determine correct branch.
  - Fix term search in non-english languages.
  - Meaningful error message when version parameter contains full SNOMED URI instead of YYYYMMDD.
- Authoring
  - Cleaner incremental semantic index updates.
  - Promote release patch function only sets component effectiveTime if blank.
  - Exclude parents of GCI axioms when calculating concept Authoring Form.
- Build
  - Make unit test elasticsearch node startup time configurable using `-Dtest.elasticsearch.start-timeout-mins=10`.


## 4.8.0 Release - 2019-03-20

This release includes a new cross-extension term search. Find it under "MultiSearch" in the Swagger docs. The functionality can be used in the [public browser](https://browser.ihtsdotools.org/) under "Go browsing... All Editions".

This release also includes some important stability fixes for extension management, the daily build process and branch merging during authoring.

### Features
- New API for searching across all loaded code systems (Editions and Extensions).
### Improvements
- Fix #102 Add moduleId in relationship target of concept browser format.
- Code System response now includes `dependantVersionEffectiveTime`.
- Pretty print for JSON responses.
- FSN selected using language only when no language refset included in the release (Australian Edition).
- Snowstorm reconnects automatically if Classification Service is restarted.
- Classification Service results are processed concurrently.
- Admin functions for content management and fixes:
  - Delete inferred relationships which are not present in a provided file.
  - Patch function to merge fixes to the last release commit, back in time (during an authoring cycle).
  - Function to find any duplicate component versions and hide the version from the parent branch.
  - Function to clone a task branch, for debugging authoring content.
- New Snowstorm logo. SVG included.
### Fixes
- Upgrading extensions with 300K+ refset members (e.g. US Edition) no longer fails. Better batch processing.
- Daily Build fixes
  - Local filesystem source configuration fixed.
  - Process no longer reverts an extension upgrade commit.
  - Process no longer reverts an extension creation commit.
- RF2 import no longer fails if reference set member has multiple trailing empty columns.
- MRCM type-ahead excludes inactive terms.
- Rebase merge can no longer cause duplicate components. Branch review is now mandatory to rebase a diverged branch and the scope of branch reviews has been corrected.
- Code System field `dependantVersion` has been removed from responses because it did not function correctly.


## 4.5.0 Release - 2019-11-20

Some small features and enhancements for the community and to support an update to the SNOMED International public browser.

### Features
- Pull request #85 New config flag to make concept bulk-load accessible when in read-only mode.
- API to list all active semantic tags including concept count.
- Descendants count option on concept, children and parents API endpoints.
- Type filter on Description search.
- API for deletion of single descriptions and relationships including force flag.
### Improvements
- Updates to documentation on extension management.
- FHIR API
  - Make JSON the default response.
  - Support expansion examination of the "compose" element.
- Fix #80 Human readable error when Snowstorm fails to connect to Elastic.
- Prevent description aggregation requests which take a very long time.
- Add conceptIds parameter to browser concept list API.
- Upgrade SNOMED Drools Engine.
### Fixes
- Fix URL mapping for bulk concept validation.
- Fix delta import marking unpublished reference set members as released.



## 4.4.0 Release - 2019-10-11 - International Authoring platform using Snowstorm

Since 4.1.0 we have made many minor and patch releases during preparation for another Snowstorm milestone.
I am very pleased to announce that we have now gone live with Snowstorm as the Terminology Server for the SNOMED International Edition Authoring Platform!

As usual we have also had plenty of engagement from the community with many questions, issues and pull requests coming through. Thank you.

Please note the new approach to importing and upgrading extensions. The Code System _migrate_ function is now deprecated in favour of the new _upgrade_ function.
Code System branches should be created directly under `MAIN` rather than under a version branch. For example `MAIN/SNOMEDCT-US`.
Using the _upgrade_ function Snowstorm will rebase the Code System branch to the a specific point on the timeline of the parent branch where the requested version
was created, without having to use release branches like `MAIN/2019-07-31`.

I hope you find this release useful and as always please just reach out or raise an issue if you have questions.

### Features
- New approach to upgrading extensions using Code System upgrade function. (_Migrate_ function now deprecated).
  - This allows extensions to live directly under the main International Edition branch rather than under a version branch.
- Concept attributes and axioms sorted in all hierarchies using International Authoring Team's preferred order for best readability.
- Description search language filter.
- Pull request #77 AWS ElasticSearch request signing.
- Ability to import daily build RF2 automatically from S3.
- Daily build authoring statistics endpoint.
- Content report for concepts made inactive with no association.
  - GET /{branch}/report/inactive-concepts-without-association
- Authoring:
  - Admin function to rollback a commit.

### Improvements
- Implemented #75 Option to enable unlimited pagination of full concept format using `snowstorm.rest-api.allowUnlimitedConceptPagination=true` (disabled by default).
- Implemented #74 Set default upload size limit to 1024MB.
- FHIR:
  - Make json the default
  - Minor updates to FHIR readme.
- Ability to filter RF2 Snapshot Export by effectiveTime.
- Upgrade baked-in MRCM XML to 1.25.0 - matching the July 2019 International Edition.
- Search:
  -  Issue #41 Configure Danish, Norwegian and Finnish alphabet additional characters.
- Authoring:
  - Prevent duplicate historical associations and inactivation reasons during branch merge.
  - API to Load Concept version from base branch timepoint.
  - API to delete single descriptions including force option.
  - API to delete single relationships including force option.
  - Log more information while processing large classification results.
- Browser:
  - Add default defaultLanguageReferenceSets to Code Systems.
- Updated docker compose and readme.
- MRCM typeahead performance improvement.
- Stateless HTTP session management to prevent memory leak.
- Update IHTSDO maven repos to nexus3.

### Fixes
- Fix issue #62 Concept search using non-alphanumeric characters
- Fix issue #78 Branch path format validation.
- Fix search results totals not consistent for the three active status options.
- Changing code system default language no longer requires restart.
- Fix inferredNotStated lookup for classifications with over 1K results.
- Prevent duplicate results when using a concept id in the search "term" field.
- Minor load test harness fixes.
- Admin operation to remove duplicate versions of donated content in version control.
- Fixed search result sorting when descriptions grouped by concept.
- RF2 Import:
  - Fix clearing release status of components when new version imported via RF2.
  - Fix Unpublished refset components being marked as released during RF2 delta import when no effective time given.
- Authoring Validation:
  - Fix Incorrect Drools semantic tag warning related to inactive FSNs.
  - Fix Drools not detecting use of duplicate synonym in same hierarchy.
  - Fix Drools warning for duplicate FSNs which are inactive.
  - Fix Drools inbound relationship check to use axioms.
  - Fix validation endpoint JSON / URL mapping issue.
- Authoring:
  - Fix Rebase failing when branch has large number of semantic changes
  - Prevent duplicate concepts in semantic index during promotion.
- Fix thread safety issue in branch review function.
- Stop making inferred relationships inactive during concept inactivation. Classification must do this.
- Semantic index: improve depth counting, error reporting and handling.
- Fix Changing published inactivation indicator does not come back consistently.
- Fix Concept deletion orphaning active axioms.
- Allow transitive closure loop during rebase commit.



## 4.1.0 Release - 2019-08-07 - Public API supporting the SNOMED browser

This major version includes the API for the SNOMED International public SNOMEDCT browser!
The browser descriptions endpoint is now faster and includes the full set of aggregations and filters to support the browser search.

Another new feature is enhanced character matching for non-english languages.
Diacritic characters which are considered as additional letters in the alphabet of a language can be added to configuration to have them indexed correctly for search.
For example the Swedish language uses the characters 'å', 'ä' and 'ö' as additional letters in their alphabet, these letters are not just accented versions of 'a' and 'o'.
Thank you to Daniel Karlsson for educating us about this and providing an initial proof of concept.

Thank you to everyone who asked questions and provided feedback during another great release.

_Note: The old public browser API project "sct-snapshot-rest-api" has now been archived in favour of the Snowstorm terminology server._

### Breaking
- Description index mapping has been updated with better support for non-english languages.
Please migrate existing data to the new mapping using the [reindexing guide](docs/index-mapping-changes.md) then run
the new admin "Rebuild the description index" function found in the swagger API docs.

### Features
- Search: Enhanced character matching for non-english languages (configured under "Search International Character Handling" in application.properties).
- Full set of aggregations and filters for browser description API endpoint.
- New concept references endpoint.
- New aggregated browser reference set members endpoint for refset summary view.
- FHIR:
  - Valueset maintenance / CRUD operations.
  - Language support - search appropriate language refset.

### Improvements
- Scalability:
  - Branch merge operation data now persisted in Elasticsearch. No other non-persistent operational data found. Ready for testing as multi-instance authoring server.
- Browser description search:
  - Faster aggregations.
  - New search parameters: active, semanticTag, module, conceptRefset.
  - New options: group by concept.
  - New search mode for regular expressions.
- Browser:
  - Browser Concept JSON format made consistent with Snow Owl (minor changes made on both sides).
- Code system listing enhanced with languages, modules and latest release.
- OWL:
  - Use latest International stemming axioms to link Object Properties and Data Properties to the main Class hierarchy.
- Configuration:
  - Added extension module mapping for Estonia, Ireland, India and Norway extensions.
- FHIR:
  - Add Postman link to FHIR docs.
  - Upgrade to HAPI 3.8.0.
  - Add filter support for extensionally defined valuesets - on expansion.
- Authoring:
  - Add attribute grouped/ungrouped validation.
  - Semantic index processing of new object attribute axioms.
  - Automatically add description non-current indicators when inactive concept saved.
  - Automatically inactivate lang refset members when inactive description saved.
  - Validation: GCI must contain at least one parent and one attribute.
- Classification:
  - Equivalent concepts response format compatible with Snow Owl.
  - Add inferred not previously stated flag to relationship changes in classification report.
- Version Control:
  - Rebase no longer changes the base timepoint of the original branch version.
  - Allow loading concepts from branch base timepoint using GET /browser/{branch}@^/concept/{conceptId}.
  - Log commit duration.
- RF2 Import:
  - Change skipped components warning message to info.
- Code Build:
  - Allow Elasticsearch unit tests to run when disk low.
  - Replace Cobertura maven plugin with Jacoco.
  - Fix all lgtm.com automated code review suggestions.
- Deployment:
  - Debian package uses urandom for SecureRandom session IDs.

### Fixes
- Fix issue #16 Return complete list of code system versions.
- Fix issue #49 Correct total results count in simple concept search.
- Fix issue #53 Incorrect ECL ancestor count after delta import.
  - Account for multiple ancestors in semantic index update concept graph.
- Fix issue #55 Security fix - upgrade embedded Tomcat container to 8.5.41.
- FHIR:
  - Correct ICD-10 URI and allow reverse map lookup.
  - Don't show refset membership in system URI.
  - Protect against null pointer if VS is not found.
- Search:
  - Use translated FSN and PT in concept browser response.
  - Fix concept search when combining ECL and definition status.
  - Concept search using concept id can now return inactive concepts.
  - Fix active flag concept filter.
- Version Control:
  - Fix branch rebase issue where multiple versions of a component could survive.   
  - Fix performance issue when promoting a large amount of changes to MAIN.
  - Branch merge review now checks changes on all ancestor branches not just the parent branch.
- Authoring validation:
  - Drools: Multiple hierarchy error should not use inferred form.
  - No traceability logging when concept updated with no change.
- Other:
  - Fix classification job date formats.
  - Concept browser format returns PT in PT field rather than FSN.



## 3.0.3 Release - 2019-05-17

This major version has support for Complete OWL SNOMED releases with no need for any active stated relationships.
It also supports authoring using OWL axioms.

Thanks again to everyone who got involved in the questions, issues and fixes during this release!

### Breaking
- Elasticsearch reindex is required.
  - Indices have been renamed to a simpler, more readable format. For example `es-rel` has been renamed to `relationship`.
  - Default number of shards per index has been changed to 1 (configurable in application.properties).
- Renamed concept additionalAxioms field to classAxioms.
- Rename classification branch metadata keys inline with RVF.

### Features
- OWL Axiom Support:
  - Import complete OWL versions of SNOMED CT.
  - Stated hierarchy navigation using axioms.
  - Stated ECL using axioms.
  - Authoring using only axioms without any active stated relationships.
  - Concept definition status set automatically using axiom definition status.
- Search:
  - Description search semantic tag counts and filtering.
  - New refset member search with aggregations (totals per reference set).
  - Search for refset members by a concept id within an OWL axiom expression.
  - Search for refset members containing OWL axiom class axioms or GCI axioms.
- FHIR:
  - Support for multiple languages
    - Accept-Language request header respected.
    - Valueset expand operation 'displayLanguage' and 'designations' parameters supported.
  - Add support for expand operation 'filter' parameter.
  - Add support for offset, count and total in ValueSet expand operation.
  - Add support for CodeSystem lookup properties.
  - Add support for all implicitly defined FHIR valuesets.
  - Add support for maps (ICD-10, CTV-3) including historical associations.
- Productionisation:
  - New multithreaded load test harness (ManualLoadTest.java) can be used to simulate many authoring users in order to evaluate different deployment configurations.
  - Concept searchAfter parameter allows scrolling through more than 10K results.
- Extensions:
  - Basic extension upgrade support (via POST /codesystems/{shortName}/migrate).
- Other:
  - Added reference set member create and update functionality to REST API.
  - Ability to load concepts from version control history within the same branch.

### Improvements
- Elasticsearch:
  - Recommended Elasticsearch version updated to 6.5.4.
  - Number of index shards and replicas is now configurable. Defaults to 1 shard and 0 replicas.
- FHIR:
  - Upgrade FHIR API from DSTU3 to R4.
  - Allow valueset expansion against other imported editions.
  - Allow FHIR requests to access MAIN (or equivalent) as well as versioned branches.
  - Automatically populate server capabilities version from maven pom.
  - Update documentation:  paging and filtering, valueset defined via refset.
- Authoring:
  - Automatically remove inactivation members when concept made active.
  - Automatically remove lang refset members which description made inactive.
  - Ensure refset member effectiveTime is updated during changes.
  - Exclude synonyms when finding conflicts during branch merge.
  - OWL Axioms included in integrity check functionality.
  - Branch lock metadata added to describe currently running commit.
  - New SNOMED Drools Engine validation engine with axiom support.
  - Many Drools validation fixes.
  - Ability to reload validation assertions and resources.
  - Updated baked in MRCM XML.
- Classification:
  - Classification save stale results check.
  - Allow saving classification results with greater than 10K changes.
  - Classification results can change existing relationships.
- Branch merging:
  - Details of integrity issues found during a promotion included in API response.
  - Exclude non-stated relationships.
  - Concepts can be manually deleted during branch merge review.
- RF2 Export:
  - New reference set types for RF2 export including: Description Type, Module Dependency and Simple Type. Note that Snowstorm does not yet calculate the Module Dependency members.
  - Carriage return line endings in RF2 export in line with RF2 specification.
  - Combine OWL Axiom and OWL Ontology refsets in RF2 export.
  - Add transientEffectiveTime option in RF2 export.
- Improved Docker instructions.
- ECL slow query logging.
- Branch metadata can contain objects.
- Binary for Elasticsearch unit tests cached in user home directory.
- Base, head and creation date stamps and epoch milliseconds on branch response.
- Authoring traceability logging of inferred changes capped to 100 (configurable).
- Moved semantic index rebuild endpoint to admin area in Swagger.
- Refset member search allows ECL in referenceSet parameter.
- Authoring traceability appended to separate log file.

### Fixes
- FHIR:
  - Fix finding latest code system version.
- Remove extra tab in header of some refset export files.
- Fix for attribute group disjunction in ECL.
- Clean up concept Elasticsearch auto-mapping, remove unpopulated fields.
- Automatically create snomed-drools-rules directory if missing.
- Additional axioms and GCI axioms included in branch merge.
- Identify branch merge conflict when concept deleted on either side of the merge.
- Prevent importing three bad relationships from international snapshot as stated and inferred.
- Version endpoint format corrected to JSON.



## 2.2.3 Fix Release - 2019-04-23

### Fixes
- Fix concept descendants endpoint for stated and inferred.



## 2.2.2 Fix Release - 2019-04-01

### Improvements
- Clarify documentation for extension loading
### Fixes
- UK Edition import fixes



## 2.2.1 Fix Release - 2019-03-29

### Fixes
- FHIR API fix, remove Accept-Language for now, incompatible annotation



## 2.2.0 Release - 2019-03-15
Maintenance release with fixes and enhancements.

Thanks to everyone who raised an issue or provided a pull request for this maintenance release.

_NOTICE - The next major release will be 3.x which will introduce support
for SNOMED CT Editions with a completely axiom based stated form._

### Breaking
- Removal of partial support for concept search using ESCG in favour of ECL.

### Features
- Issue #14 Language/Extension support in FHIR API (PR from @goranoe).
  - Added module to CodeSystem lookup table to support this.
- Issue #18 Command line --exit flag shuts down Snowstorm after loading data.
- Added Elasticsearch basic authentication configuration options.
- Support for latest RF2 OWL reference set file naming.
- Added low level single concept endpoint.
- Added concept search definition status filter.

### Improvements
- Issue #28 Better non-english character support in ECL parsing (by @danka74).
- Docker configuration improvements and documentation (PRs from @Zwordi and @kevinbayes).
- Many documentation updates.
- New documentation on Snowstorm FHIR support.
- New documentation on updating extensions.
- Semantic index updates are not logged if they take less than a second.
- Added "Snowstorm startup complete" log message.
- Refactoring recommendations from lgtm.com.
- Allow branch specific MRCM XML configuration.
- Removed unused feature which allowed mirrored authoring via traceability feed.
- New ascii banner on startup.
- Concept search uses stated form unless inferred ecl given (better during authoring and has no effect on released content).
- Fail faster when concept page is above 10K (ES does not support this with default config).

### Fixes
- Issue #29 Escape concept term quotes in search results.
- Fix concept parents listing.
- Fix ECL dot notation against empty set of concepts.
- Fix ECL conjunction with reverse flag.
- MRCM API domain attributes returns 'is a' attribute if no parents specified.
- MRCM API allows subtypes of MRCM attributes.
- Fix reloading MRCM rules API mapping.
- Catch classification save error when branch locked.
- Fix missing destination expansion in relationship endpoint
- Prevent crosstalk in Elasticsearch integration tests.



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

### Fixes
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
