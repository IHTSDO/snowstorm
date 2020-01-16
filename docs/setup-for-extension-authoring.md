# Setup for SNOMED CT Extension / Edition Authoring
Snowstorm can be used to author an extension of SNOMED CT. 

In 2020 the SNOMED International managed service offering will use Snowstorm as the authoring Terminology Server.

## Code System and Data Import
- Start by setting up the code system and importing the RF2 using the [loading extensions guide](updating-snomed-and-extensions.md). 

## Branch Metadata
Branch metadata is used to store various items of configuration for each code system being maintained. 
The metadata is set on the root branch of the code system but is applies to all ancestor branches unless different values are given there.
Branch metadata can be set using the update branch endpoint in the API.


| Metadata Key    | Purpose             | Example        |
|-----------------|---------------------|----------------|
| defaultModuleId | Used during authoring. The module set on all components created or updated through the API. | "45991000052106" |
| defaultNamespace | Used during authoring. The namespace for SCTIDs generated for new components. | "1000005" |
| assertionGroupNames | Used for validation during authoring. [SNOMED Drools Rule](https://github.com/IHTSDO/snomed-drools-rules) assertion group names used for validation. | "common-authoring,dk-authoring" |
| dependencyPackage | Used for classification, reporting and RVF validation. Name of RF2 archive which includes a Snapshot of the edition or extension release that this extension depends on. | "SnomedCT_InternationalRF2_PRODUCTION_20190731T120000Z.zip" |
| previousPackage | Same use as above. Name of RF2 archive which includes a Snapshot of the last release of this extension. Should not be set for new extensions. | "SnomedCT_ManagedServiceDK_PRODUCTION_DK1000005_20190930T120000Z.zip" |
| previousRelease | Used by the [RVF](https://github.com/IHTSDO/release-validation-framework). Effective time of the previous extension release. | "20190930" |
| dependencyRelease | Used by the [RVF](https://github.com/IHTSDO/release-validation-framework). Effective time of the edition or extension which this extension extends. | "20190731" |
