package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRPackageIndex;
import org.snomed.snowstorm.fhir.domain.FHIRPackageIndexFile;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class FHIRLoadPackageService {

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private FHIRConceptService fhirConceptService;

	@Autowired
	private FhirContext fhirContext;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void uploadPackageResources(File packageFile, Set<String> resourceUrlsToImport) throws IOException {
		JsonParser jsonParser = (JsonParser) fhirContext.newJsonParser();
		FHIRPackageIndex index = extractObject(packageFile, ".index.json", FHIRPackageIndex.class, jsonParser);
		Set<String> supportedResourceTypes = Set.of("CodeSystem");
		boolean importAll = resourceUrlsToImport.contains("*");
		List<FHIRPackageIndexFile> filesToImport = index.getFiles().stream()
				.filter(file -> (importAll && supportedResourceTypes.contains(file.getResourceType())) ||
						(!importAll && resourceUrlsToImport.contains(file.getUrl())))
				.collect(Collectors.toList());
		validateResources(filesToImport, resourceUrlsToImport, importAll, supportedResourceTypes);
		logger.info("Importing {} resources, found within index of package {}.", filesToImport.size(), packageFile.getName());

		for (FHIRPackageIndexFile indexFileToImport : filesToImport) {
			if (indexFileToImport.getResourceType().equals("CodeSystem")) {
				CodeSystem codeSystem = extractObject(packageFile, indexFileToImport.getFilename(), CodeSystem.class, jsonParser);
				if (FHIRHelper.isSnomedUri(codeSystem.getUrl())) {
					logger.info("Skipping import of SNOMED CT code system via package. Please use the native SNOMED-CT API RF2 import.");
					continue;
				}
				String url = indexFileToImport.getUrl();
				String version = indexFileToImport.getVersion();
				FHIRCodeSystemVersion existingCodeSystemVersion = codeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(url).setVersion(version));
				if (existingCodeSystemVersion != null) {
					throw FHIRHelper.exception(format("Resource %s/%s already exists. Please delete this version before attempting to import.",
									"CodeSystem", existingCodeSystemVersion.getId()),
							OperationOutcome.IssueType.NOTSUPPORTED, 400);
				}
				List<CodeSystem.ConceptDefinitionComponent> concepts = codeSystem.getConcept();
				logger.info("Importing CodeSystem {} with {} concepts from package", codeSystem.getUrl(), concepts != null ? concepts.size() : 0);
				FHIRCodeSystemVersion codeSystemVersion = codeSystemService.save(codeSystem);
				if (concepts != null) {
					fhirConceptService.saveAllConceptsOfCodeSystemVersion(concepts, codeSystemVersion);
				}
			}
		}
	}

	private static void validateResources(List<FHIRPackageIndexFile> filesToImport, Set<String> resourceUrlsToImport, boolean importAll, Set<String> supportedResourceTypes) {
		for (FHIRPackageIndexFile fhirPackageIndexFile : filesToImport) {
			if (importAll && !supportedResourceTypes.contains(fhirPackageIndexFile.getResourceType())) {
				throw FHIRHelper.exception(format("Resource type '%s' is not supported for package based import.", fhirPackageIndexFile.getResourceType()),
						OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			if (fhirPackageIndexFile.getVersion() == null) {
				fhirPackageIndexFile.version = "1";
			}
		}
		if (!importAll) {
			Set<String> resourcesNotFound = new HashSet<>(resourceUrlsToImport);
			resourcesNotFound.removeAll(filesToImport.stream().map(FHIRPackageIndexFile::getUrl).collect(Collectors.toList()));
			if (!resourcesNotFound.isEmpty()) {
				throw FHIRHelper.exception(format("Failed to find resources (%s) within package index.", resourcesNotFound), OperationOutcome.IssueType.NOTFOUND, 400);
			}
		}
	}

	private <T> T extractObject(File packageFile, String archiveEntryName, Class<T> clazz, JsonParser jsonParser) throws IOException {
		try (GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(new FileInputStream(packageFile));
			 TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {

			ArchiveEntry entry;
			while ((entry = tarIn.getNextEntry()) != null) {
				if (entry.getName().replace("package/", "").equals(archiveEntryName)) {
					System.out.println("Mapping " + entry.getName());
					if (archiveEntryName.equals(".index.json")) {
						return mapper.readValue(tarIn, clazz);
					} else {
						IBaseResource iBaseResource = jsonParser.parseResource(tarIn);
						System.out.println(iBaseResource.getClass());
						return (T) iBaseResource;
					}
				}
			}
		}
		throw FHIRHelper.exception(format("File '%s' not found within package.", archiveEntryName), OperationOutcome.IssueType.NOTFOUND, 401);
	}

}
