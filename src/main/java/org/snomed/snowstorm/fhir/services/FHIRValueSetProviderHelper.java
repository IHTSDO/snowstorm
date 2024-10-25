package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.Nullable;
import org.snomed.snowstorm.fhir.domain.FHIRPackageIndex;
import org.snomed.snowstorm.fhir.domain.FHIRPackageIndexFile;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;


class FHIRValueSetProviderHelper {
	private static FhirContext ctx;
	static{
		// Create a FHIR context
		ctx = FhirContext.forR4();
	}

	static ValueSetExpansionParameters getValueSetExpansionParameters(IdType id, final List<Parameters.ParametersParameterComponent> parametersParameterComponents) {
		Parameters.ParametersParameterComponent valueSetParam = findParameterOrNull(parametersParameterComponents, "valueSet");
		try {
			return new ValueSetExpansionParameters(
					id != null ? id.getIdPart() : null,
					valueSetParam != null ? (ValueSet) valueSetParam.getResource() : null,
					new URI(findParameterStringOrNull(parametersParameterComponents, "url")),
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
					findParameterStringOrNull(parametersParameterComponents, "version"));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
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
			final StringType version) {

		try {
			return new ValueSetExpansionParameters(
					id != null ? id.getIdPart() : null,
					null,
					url != null ? new URI(url.getValueAsString()) : null,
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
					getOrNull(version));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Nullable
	static String getOrNull(StringType stringType) {
		return stringType != null ? stringType.toString() : null;
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

	public static byte[] createNpmPackageFromResources(List<Resource> resources) {

		class Tuple{
			public FHIRPackageIndexFile indexFile;
			public String string;
		}

		List<Tuple> tuples = resources.stream().map(x -> {
					Tuple tuple = new Tuple();
					FHIRPackageIndexFile temp = new FHIRPackageIndexFile();
					temp.id = x.getIdPart();
					temp.url = x.getNamedProperty("url").getValues().get(0).primitiveValue();
					temp.resourceType = x.getResourceType().toString();
					temp.filename = "%s-%s.json".formatted(temp.resourceType, temp.id);
					temp.version = x.getNamedProperty("version").getValues().get(0).primitiveValue();

					tuple.indexFile = temp;



			// Instantiate a new JSON parser
			IParser parser = ctx.newJsonParser();

			// Serialize it
			String serialized = parser.encodeResourceToString(x);
					tuple.string = serialized;

					return tuple;
				}
		).toList();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();





		try (TarArchiveOutputStream gzOut = new TarArchiveOutputStream(
					new GZIPOutputStream(
							baos)))
		{


			for (Tuple tuple : tuples) {
				TarArchiveEntry entry = new TarArchiveEntry("package/" + tuple.indexFile.getFilename());
				entry.setSize(tuple.string.getBytes().length);
				gzOut.putArchiveEntry(entry);
				gzOut.write(tuple.string.getBytes());
				gzOut.closeArchiveEntry();
			}

			class FHIRIndex{
				public List<FHIRPackageIndexFile> files;
			}

			FHIRIndex fi = new FHIRIndex();
			fi.files = tuples.stream().map(x -> x.indexFile).toList();
			ObjectMapper mapper = new ObjectMapper();
			String index = mapper.writeValueAsString(fi);

			TarArchiveEntry entry = new TarArchiveEntry("package/.index.json");
			entry.setSize(index.getBytes().length);

			gzOut.putArchiveEntry(entry);
			gzOut.write(index.getBytes());
			gzOut.closeArchiveEntry();



		} catch (IOException e) {
			throw new RuntimeException(e);
		}


		byte[] out = baos.toByteArray();

		try (FileOutputStream fop = new FileOutputStream("zipke.zip")) {
			fop.write(out);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return out;

	}


}
