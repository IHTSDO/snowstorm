package org.snomed.snowstorm.core.data.services;

public interface BranchMetadataKeys {
	String PREVIOUS_PACKAGE = "previousPackage";// Used for classification
	String DEPENDENCY_PACKAGE = "dependencyPackage";// Used for classification of extensions
	String PREVIOUS_DEPENDENCY_PACKAGE = "previousDependencyPackage"; //Used for building previous Snapshot eg for RP SCS
	String ASSERTION_GROUP_NAMES = "assertionGroupNames";
	String REQUIRED_LANGUAGE_REFSETS = "requiredLanguageRefsets";
	String DEFAULT_MODULE_ID = "defaultModuleId";
	String EXPECTED_EXTENSION_MODULES = "expectedExtensionModules";
	String DEFAULT_NAMESPACE = "defaultNamespace";
	String SHORTNAME = "shortname";
	String DEPENDENCY_RELEASE = "dependencyRelease";
	String PREVIOUS_RELEASE = "previousRelease";
	String ASSERTION_EXCLUSION_LIST = "assertionExclusionList";
	String IMPORT_TYPE = "importType";
}
