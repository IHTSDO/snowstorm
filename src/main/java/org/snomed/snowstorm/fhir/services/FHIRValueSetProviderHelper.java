package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snomed.snowstorm.fhir.domain.FHIRPackageIndexFile;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;

class FHIRValueSetProviderHelper {

	private static FhirContext ctx;
	static{
		// Create a FHIR context
		ctx = FhirContext.forR4();
	}

	private FHIRValueSetProviderHelper() {
		// Prevent instantiation
	}

	static ValueSetExpansionParameters getValueSetExpansionParameters(IdType id, final List<Parameters.ParametersParameterComponent> parametersParameterComponents) {
		Parameters.ParametersParameterComponent valueSetParam = findParameterOrNull(parametersParameterComponents, "valueSet");
		return new ValueSetExpansionParameters(
			id != null ? id.getIdPart() : null,
			valueSetParam != null ? (ValueSet) valueSetParam.getResource() : null,
			findParameterStringOrNull(parametersParameterComponents, "url"),
			findParameterStringOrNull(parametersParameterComponents, "valueSetVersion"),
			findParameterStringOrNull(parametersParameterComponents, "context"),
			findParameterStringOrNull(parametersParameterComponents, "contextDirection"),
			findParameterStringOrNull(parametersParameterComponents, "filter"),
			findParameterStringOrNull(parametersParameterComponents, "date"),
			findParameterIntOrNull(parametersParameterComponents, "offset"),
			findParameterIntOrNull(parametersParameterComponents, "count"),
			findParameterBooleanOrNull(parametersParameterComponents, "includeDesignations"),
			findParameterStringListOrNull(parametersParameterComponents, "designation"),
			findParameterBooleanOrNull(parametersParameterComponents, "includeDefinition"),
			findParameterBooleanOrNull(parametersParameterComponents, "activeOnly"),
			findParameterBooleanOrNull(parametersParameterComponents, "excludeNested"),
			findParameterBooleanOrNull(parametersParameterComponents, "excludeNotForUI"),
			findParameterBooleanOrNull(parametersParameterComponents, "excludePostCoordinated"),
			findParameterStringOrNull(parametersParameterComponents, "displayLanguage"),
			findParameterCanonicalOrNull(parametersParameterComponents, "exclude-system"),
			findParameterCanonicalOrNull(parametersParameterComponents, "system-version"),
			findParameterCanonicalOrNull(parametersParameterComponents, "check-system-version"),
			findParameterCanonicalOrNull(parametersParameterComponents, "force-system-version"),
			findParameterStringOrNull(parametersParameterComponents, "version"),
			findParameterStringOrNull(parametersParameterComponents, "property"),
			findParameterCanonicalOrNull(parametersParameterComponents, "default-valueset-version"),
			false
		);
	}

	static ValueSetExpansionParameters getValueSetExpansionParameters(
			final IdType id,
			final UriType url,
			final String valueSetVersion,
			final String context,
			final String contextDirection,
			final String filter,
			final String date,
			final IntegerType offset,
			final IntegerType count,
			final BooleanType includeDesignations,
			final List<String> designations,
			final BooleanType includeDefinition,
			final BooleanType activeOnly,
			final BooleanType excludeNested,
			final BooleanType excludeNotForUI,
			final BooleanType excludePostCoordinated,
			final String displayLanguage,
			final StringType excludeSystem,
			final StringType systemVersion,
			final StringType checkSystemVersion,
			final StringType forceSystemVersion,
			final StringType version,
			final CodeType property,
			final CanonicalType versionValueSet) {

			return new ValueSetExpansionParameters(
					id != null ? id.getIdPart() : null,
					null,
					url != null ? url.getValueAsString() : null,
					valueSetVersion,
					context,
					contextDirection,
					filter,
					date,
					offset != null ? offset.getValue() : null,
					count != null ? count.getValue() : null,
					getOrNull(includeDesignations),
					designations,
					getOrNull(includeDefinition),
					getOrNull(activeOnly),
					getOrNull(excludeNested),
					getOrNull(excludeNotForUI),
					getOrNull(excludePostCoordinated),
					displayLanguage,
					CanonicalUri.fromString(getOrNull(excludeSystem)),
					CanonicalUri.fromString(getOrNull(systemVersion)),
					CanonicalUri.fromString(getOrNull(checkSystemVersion)),
					CanonicalUri.fromString(getOrNull(forceSystemVersion)),
					getOrNull(version),
					getOrNull(property),
					CanonicalUri.fromString(getOrNull(versionValueSet)),
		false
			);
	}

	@Nullable
	static String getOrNull(StringType stringType) {
		return stringType != null ? stringType.toString() : null;
	}

	@Nullable
	static String getOrNull(UrlType urlType) {
		return urlType != null ? urlType.getValueAsString() : null;
	}

	@Nullable
	static String getOrNull(CanonicalType canonicalType) {
		return canonicalType != null ? canonicalType.getValueAsString() : null;
	}

	@Nullable
	static Boolean getOrNull(BooleanType bool) {
		return bool != null ? bool.booleanValue() : null;
	}

	static Parameters.ParametersParameterComponent findParameterOrNull(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst().orElse(null);
	}

	static List<Parameters.ParametersParameterComponent> findParametersByName(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).toList();
	}

	static List<Parameters.ParametersParameterComponent> findParametersByResourceType(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String... type) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getResource().hasType(type)).toList();
	}
	public static String getFullURL(HttpServletRequest request) {
		StringBuilder requestURL = new StringBuilder(request.getRequestURL().toString());
		String queryString = request.getQueryString();

		if (queryString == null) {
			return requestURL.toString();
		} else {
			return requestURL.append('?').append(queryString).toString();
		}
	}

	private static class FileWithContents {
		FHIRPackageIndexFile indexFile;
		String content;
	}

	private static class FHIRIndex {
		private List<FHIRPackageIndexFile> files;

		public List<FHIRPackageIndexFile> getFiles() {
			return files;
		}

		public void setFiles(List<FHIRPackageIndexFile> files) {
			this.files = files;
		}
	}

	public static File createNpmPackageFromResources(List<Resource> resources) {

		List<FileWithContents> allFilesWithContents = getAllFilesWithContents(resources);

		try {
            File npmPackage = File.createTempFile("tx-resources-",".tgz");
            npmPackage.deleteOnExit();
			FileOutputStream fop = new FileOutputStream(npmPackage);
			BufferedOutputStream bop = new BufferedOutputStream(fop);
			GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bop);
			TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(gzipOutputStream);

			try {
				addContentToArchive(allFilesWithContents, tarArchiveOutputStream);
			} finally {
				tarArchiveOutputStream.finish();
				tarArchiveOutputStream.close();
				gzipOutputStream.close();
				bop.close();
				fop.close();
			}
			return npmPackage;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
	}

	private static void addContentToArchive(List<FileWithContents> allFilesWithContents, TarArchiveOutputStream tarArchiveOutputStream) throws IOException {
		for (FileWithContents tuple : allFilesWithContents) {
			TarArchiveEntry entry = new TarArchiveEntry("package/" + tuple.indexFile.getFilename());
			entry.setSize(tuple.content.getBytes().length);
			tarArchiveOutputStream.putArchiveEntry(entry);
			tarArchiveOutputStream.write(tuple.content.getBytes());
			tarArchiveOutputStream.closeArchiveEntry();
		}

		FHIRIndex fi = new FHIRIndex();
		fi.setFiles(allFilesWithContents.stream().map(x -> x.indexFile).toList());
		ObjectMapper mapper = new ObjectMapper();
		String index = mapper.writeValueAsString(fi);

		TarArchiveEntry entry = new TarArchiveEntry("package/.index.json");
		entry.setSize(index.getBytes().length);

		tarArchiveOutputStream.putArchiveEntry(entry);
		tarArchiveOutputStream.write(index.getBytes());
		tarArchiveOutputStream.closeArchiveEntry();
	}

	private static @NotNull List<FileWithContents> getAllFilesWithContents(List<Resource> resources) {
		return resources.stream().map(resource -> {
					FileWithContents fileWithContents = new FileWithContents();
					FHIRPackageIndexFile temp = new FHIRPackageIndexFile();
					temp.id = resource.getIdPart();
					resource.getNamedProperty("url").getValues().stream().findFirst().ifPresent(y ->  temp.url = y.primitiveValue());
					temp.resourceType = resource.getResourceType().toString();
					Optional<String> version = resource.getNamedProperty("version").getValues().stream().findFirst().map(Base::primitiveValue);
					if(version.isPresent()) {
						temp.version = version.get();
						temp.filename = "%s-%s-%s.json".formatted(temp.resourceType, temp.id, temp.version);
					} else {
						temp.filename = "%s-%s.json".formatted(temp.resourceType, temp.id);
					}
					fileWithContents.indexFile = temp;

					// Instantiate a new JSON parser
					IParser parser = ctx.newJsonParser();

					// Serialize it
            		fileWithContents.content = parser.encodeResourceToString(resource);

					return fileWithContents;
				}
		).toList();
	}
}
