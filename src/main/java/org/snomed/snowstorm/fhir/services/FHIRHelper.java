package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.DialectConfigurationService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.BranchPath;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Component
public class FHIRHelper implements FHIRConstants {

	private static final Pattern SNOMED_URI_MODULE_PATTERN = Pattern.compile("http://snomed.info/x?sct/(\\d+)");
	private static final Pattern SNOMED_URI_MODULE_AND_VERSION_PATTERN = Pattern.compile("http://snomed.info/x?sct/(\\d+)/version/([\\d]{8})");
	private static final Pattern SCT_ID_PATTERN = Pattern.compile("sct_(\\d)+_(\\d){8}");

	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); // TODO: This is not thread safe!

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private DialectConfigurationService dialectService;

	private FhirContext fhirContext;

	public static final Sort DEFAULT_SORT = Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending();
	public static final Sort MEMBER_SORT = Sort.sort(ReferenceSetMember.class).by(ReferenceSetMember::getMemberId).descending();
	
	private static final Logger logger = LoggerFactory.getLogger(FHIRHelper.class);

	public static boolean isSnomedUri(String uri) {
		return uri != null && (uri.startsWith(SNOMED_URI) || uri.startsWith(SNOMED_URI_UNVERSIONED));
	}

	public static Integer getSnomedVersion(String versionStr) throws FHIROperationException {
		if (versionStr.contains(UNVERSIONED) || versionStr.startsWith(SNOMED_URI_UNVERSIONED)) {
			return UNVERSIONED_SCT_VERSION;
		}

		try {
			return !versionStr.contains(VERSION)
					? null
					: Integer.parseInt(versionStr.substring(versionStr.indexOf(VERSION) + VERSION.length()));
		} catch (NumberFormatException e) {
			throw new FHIROperationException(IssueType.CONFLICT, "Version expected to be numeric in format YYYYMMDD.");
		}
	}

	static String translateDescType(String typeSctid) {
		switch (typeSctid) {
			case Concepts.FSN : return "Fully specified name";
			case Concepts.SYNONYM : return "Synonym";
			case Concepts.TEXT_DEFINITION : return "Text definition";
		}
		return null;
	}

	public static SnowstormFHIRServerResponseException exception(String message, IssueType issueType, int theStatusCode) {
		OperationOutcome outcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent component = new OperationOutcome.OperationOutcomeIssueComponent();
		component.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		component.setCode(issueType);
		component.setDiagnostics(message);
		outcome.addIssue(component);
		return new SnowstormFHIRServerResponseException(theStatusCode, message, outcome);
	}

	public static String findParameterStringOrNull(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst()
				.map(Objects::toString).orElse(null);
	}

	public static CanonicalUri findParameterCanonicalOrNull(final List<Parameters.ParametersParameterComponent> parametersParameterComponents, final String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst()
				.map(Objects::toString).map(CanonicalUri::fromString).orElse(null);
	}

	public static Boolean findParameterBooleanOrNull(List<Parameters.ParametersParameterComponent> parametersParameterComponents, String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst()
				.map(param -> ((BooleanType)param.getValue()).booleanValue()).orElse(null);
	}

	@SuppressWarnings("unchecked")
	public static List<String> findParameterStringListOrNull(List<Parameters.ParametersParameterComponent> parametersParameterComponents, String name) {
		return parametersParameterComponents.stream().filter(parametersParameterComponent -> parametersParameterComponent.getName().equals(name)).findFirst()
				.map(param -> (List<String>) param.getValue()).orElse(null);
	}

	public static String getDisplayLanguage(String displayLanguageParam, String acceptHeader) {
		if (displayLanguageParam != null) {
			return displayLanguageParam;
		}
		if (acceptHeader != null) {
			return acceptHeader;
		}
		return "en";
	}

	public static class SnowstormFHIRServerResponseException extends BaseServerResponseException {
		public SnowstormFHIRServerResponseException(int theStatusCode, String theMessage, IBaseOperationOutcome theBaseOperationOutcome) {
			super(theStatusCode, theMessage, theBaseOperationOutcome);
		}
	}

	String getSnomedEditionModule(StringType versionStr) {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() ||
				versionStr.getValueAsString().equals(SNOMED_URI) ||
				versionStr.getValueAsString().equals(SNOMED_URI_UNVERSIONED)) {
			return Concepts.CORE_MODULE;
		}
		return getSnomedEditionModule(versionStr.getValueAsString());
	}

	private String getSnomedEditionModule(String versionStr) {
		if (!versionStr.startsWith(SNOMED_URI) &&
				!versionStr.startsWith(SNOMED_URI_UNVERSIONED)) {
			throw new NotFoundException("Unknown system URI: " + versionStr + ", expected " + SNOMED_URI + " or xsct variant.");
		}
		String str = versionStr.replace(SNOMED_URI_UNVERSIONED, SNOMED_URI);
		return str.contains(FHIRConstants.VERSION) ? 
				str.substring(SNOMED_URI.length() + 1, str.indexOf(FHIRConstants.VERSION))
				: str.substring(SNOMED_URI.length() + 1);
	}

	public BranchPath getBranchPathFromURI(StringType codeSystemVersionUri) throws FHIROperationException {
		String branchPathStr;
		String defaultModule = getSnomedEditionModule(codeSystemVersionUri);
		Integer version = null;
		if (codeSystemVersionUri != null) {
			version = getSnomedVersion(codeSystemVersionUri.toString());
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem = codeSystemService.findByDefaultModule(defaultModule);
		if (codeSystem == null) {
			String msg = format("No code system known with default module of %s.", defaultModule);
			throw new FHIROperationException(IssueType.NOTFOUND, msg );
		}

		CodeSystemVersion codeSystemVersion;
		String shortName = codeSystem.getShortName();
		if (version != null) {
			// Lookup specific version, or the "daily build" branch if we detect "UNVERSIONED" as the version
			if (version.equals(UNVERSIONED_SCT_VERSION)) {
				branchPathStr = codeSystem.getBranchPath();
				logger.info("Request to use unversioned content, using daily build branchpath: " + branchPathStr);
			} else {
				codeSystemVersion = codeSystemService.findVersion(shortName, version);
				if (codeSystemVersion == null) {
					String msg = format("No branch found for Code system %s with edition version %s.", shortName, version);
					throw new FHIROperationException(IssueType.NOTFOUND, msg );
				}
				branchPathStr = codeSystemVersion.getBranchPath();
			}
		} else {
			// Lookup latest published effective version
			branchPathStr = codeSystem.getLatestVersion().getBranchPath();
		}

		if (branchPathStr == null) {
			String msg = format("No branch found for Code system %s with default module %s.", shortName, defaultModule);
			throw new FHIROperationException(IssueType.NOTFOUND, msg);
		}
		return new BranchPath(branchPathStr);
	}

	public List<LanguageDialect> getLanguageDialects(List<String> designations, String acceptLanguageHeader) throws FHIROperationException {
		// Use designations preferably, or fall back to language headers
		final List<LanguageDialect> languageDialects = new ArrayList<>();
		if (designations != null) {
			for (String designation : designations) {
				if (designation.length() > MAX_LANGUAGE_CODE_LENGTH) {
					//in this case we're expecting a designation token
					//of the form snomed PIPE langrefsetId
					String[] tokenParts = designation.split(PIPE);
					if (tokenParts.length < 2 ||
							!StringUtils.isNumeric(tokenParts[1]) ||
							// check for SNOMED URI, possibly an extension URI
							// TODO: version support?
							!tokenParts[0].matches(SNOMED_URI + "(/\\d*)?")) {
						throw new FHIROperationException(IssueType.VALUE, "Malformed designation token '" + designation + "' expected format http://snomed.info/sct(/moduleId) " +
								"PIPE langrefsetId.");
					}
					LanguageDialect languageDialect = new LanguageDialect(null, Long.parseLong(tokenParts[1]));
					if (!languageDialects.contains(languageDialect)) {
						languageDialects.add(languageDialect);
					}
				} else {
					languageDialects.add(dialectService.getLanguageDialect(designation));
				}
			}
		} else {
			if (acceptLanguageHeader != null) {
				languageDialects.addAll(ControllerHelper.parseAcceptLanguageHeader(acceptLanguageHeader));
			}
		}
		if (languageDialects.isEmpty()) {
			languageDialects.addAll(DEFAULT_LANGUAGE_DIALECTS);
		}
		return languageDialects;
	}

	public void setLanguageOptions(List<LanguageDialect> designations, String displayLanguageStr, String acceptLanguageHeader) throws FHIROperationException {
		setLanguageOptions(designations, null, displayLanguageStr, null, acceptLanguageHeader);
	}
	
	public String getPreferredTerm(Concept concept, List<LanguageDialect> designations) {
		if (designations == null || designations.isEmpty()) {
			return concept.getPt().getTerm();
		}
		
		for (Description d : concept.getDescriptions()) {
			if (d.hasAcceptability(Concepts.PREFERRED, designations.get(0)) &&
					d.getTypeId().equals(Concepts.SYNONYM)) {
				return d.getTerm();
			}
		}
		return null;
	}
	
	public boolean setLanguageOptions(List<LanguageDialect> designations,
			List<String> designationsStr,
			String displayLanguageStr,
			Boolean includeDesignationsType,
			String acceptLanguageHeader) throws FHIROperationException {

		designations.addAll(getLanguageDialects(designationsStr, acceptLanguageHeader));
		// Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		if (displayLanguageStr != null) {
			//FHIR uses dialect codes as per https://tools.ietf.org/html/bcp47
			LanguageDialect displayDialect = dialectService.getLanguageDialect(displayLanguageStr);
			//Ensure the display language is first in our list
			designations.remove(displayDialect);
			designations.add(0, displayDialect);
		} 

		//If someone specified designations, then include them unless specified not to, in which 
		//case use only for the displayLanguage because that's the only way to get a langRefsetId specified
		boolean includeDesignations;
		//Otherwise include designations if we've specified one or more
		includeDesignations = Objects.requireNonNullElseGet(includeDesignationsType, () -> designationsStr != null);
		return includeDesignations;
	}

	public void append(StringType str, String appendMe) {
		str.setValueAsString(str + appendMe);
	}

	public static void requireExactlyOneOf(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 == null && param2 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, format("One of '%s' or '%s' parameters must be supplied.", param1Name, param2Name));
		} else {
			mutuallyExclusive(param1Name, param1, param2Name, param2);
		}
	}

	public static void mutuallyExclusive(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 != null && param2 != null) {
			throw new FHIROperationException(IssueType.INVARIANT, format("Use one of '%s' or '%s' parameters.", param1Name, param2Name));
		}
	}

	public static void mutuallyRequired(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 != null && param2 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, format("Input parameter '%s' can only be used in conjunction with parameter '%s'.",
					param1Name, param2Name));
		}
	}

	public static void mutuallyRequired(String param1Name, Object param1, String param2Name, Object param2, String param3Name, Object param3) throws FHIROperationException {
		if (param1 != null && param2 == null && param3 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, format("Use of input parameter '%s' only allowed if '%s' or '%s' is also present.",
					param1Name, param2Name, param3Name));
		}
	}
	
	public static void required(String param1Name, Object param1) throws FHIROperationException {
		if (param1 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, format("Parameter '%s' must be supplied", param1Name));
		}
	}

	public static void notSupported(String paramName, Object obj) {
		notSupported(paramName, obj, null);
	}

	public static void notSupported(String paramName, Object obj, String additionalDetail) {
		if (obj != null) {
			String message = format("Input parameter '%s' is not supported%s", paramName, (additionalDetail == null ? "." : format(" %s", additionalDetail)));
			throw exception(message, IssueType.NOTSUPPORTED, 400);
		}
	}

	public void notSupportedSubsumesAcrossCodeSystemVersions(FHIRCodeSystemVersion codeSystemVersion, Coding coding) throws FHIROperationException {
		if (coding != null) {
			if ((coding.getSystem() != null && !coding.getSystem().equals(codeSystemVersion.getUrl())) ||
					(coding.getVersion() != null && coding.getVersion().equals(codeSystemVersion.getVersion()))) {
				throw new FHIROperationException(IssueType.NOTSUPPORTED, "This server does not support subsumes with different code systems in system and coding parameters.");
			}
		}
	}

	public String recoverCode(CodeType code, Coding coding) throws FHIROperationException {
		if (code == null && coding == null) {
			throw new FHIROperationException(IssueType.INVARIANT, "Use either 'code' or 'coding' parameters, not both.");
		} else if (code != null) {
			if (code.getCode().contains("|")) {
				throw new FHIROperationException(IssueType.NOTSUPPORTED, "The 'code' parameter cannot supply a codeSystem. " +
						"Use 'coding' or provide CodeSystem in 'system' parameter.");
			}
			return code.getCode();
		}
		return coding.getCode();
	}

	public FHIRCodeSystemVersionParams getCodeSystemVersionParams(StringType codeSystemParam, StringType versionParam, Coding coding) throws FHIROperationException {
		return getCodeSystemVersionParams(null, codeSystemParam, versionParam, coding);
	}

	public static FHIRCodeSystemVersionParams getCodeSystemVersionParams(IdType systemId, PrimitiveType<?> codeSystemParam, StringType versionParam, Coding coding) throws FHIROperationException {
		return getCodeSystemVersionParams(systemId != null ? systemId.getIdPart() : null, codeSystemParam != null ? codeSystemParam.getValueAsString() : null,
				versionParam != null ? versionParam.toString() : null, coding);
	}

	public static FHIRCodeSystemVersionParams getCodeSystemVersionParams(String systemId, String codeSystemParam, String versionParam, Coding coding) throws FHIROperationException {
		if (codeSystemParam != null && coding != null && coding.getSystem() != null && !codeSystemParam.equals(coding.getSystem())) {
			throw new FHIROperationException(IssueType.CONFLICT, "Code system defined in system and coding do not match.");
		}
		if (versionParam != null && coding != null && coding.getVersion() != null && !versionParam.toString().equals(coding.getVersion())) {
			throw new FHIROperationException(IssueType.CONFLICT, "Version defined in version and coding do not match.");
		}

		String codeSystemUrl = null;
		if (codeSystemParam != null) {
			codeSystemUrl = codeSystemParam;
		} else if (coding != null) {
			codeSystemUrl = coding.getSystem();
		}
		if (codeSystemUrl == null && systemId == null) {
			throw new FHIROperationException(IssueType.CONFLICT, "Code system not defined in any parameter.");
		}

		String version = null;
		if (versionParam != null) {
			version = versionParam;
		} else if (coding != null) {
			version = coding.getVersion();
		}

		FHIRCodeSystemVersionParams codeSystemParams = new FHIRCodeSystemVersionParams(codeSystemUrl);
		if (version != null) {
			if ("*".equals(version)) {
				throw FHIRHelper.exception("Version '*' is not supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			if (codeSystemParams.isSnomed()) {
				// Parse module and version from snomed version URI
				// Either "http://snomed.info/sct/[sctid]" or "http://snomed.info/sct/[sctid]/version/[YYYYMMDD]"
				Matcher matcher;
				String versionWithoutParams = version.contains("?") ? version.substring(0, version.indexOf("?")) : version;
				if ((matcher = SNOMED_URI_MODULE_PATTERN.matcher(versionWithoutParams)).matches()) {
					codeSystemParams.setSnomedModule(matcher.group(1));
				} else if ((matcher = SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionWithoutParams)).matches()) {
					if (codeSystemParams.isUnversionedSnomed()) {
						throw new FHIROperationException(IssueType.CONFLICT, "A specific version can not be requested when using " +
								"the '" + SNOMED_URI_UNVERSIONED + "' code system.");
					}
					codeSystemParams.setSnomedModule(matcher.group(1));
					codeSystemParams.setVersion(matcher.group(2));
				} else {
					throw new FHIROperationException(IssueType.CONFLICT, format("The version parameter for the '" + SNOMED_URI + "' system must use the format " +
							"'http://snomed.info/sct/[sctid]' or http://snomed.info/sct/[sctid]/version/[YYYYMMDD]. Version provided does not match: '%s'.", versionWithoutParams));
				}
			} else {
				// Take version param literally
				codeSystemParams.setVersion(version);
			}
		}
		if (systemId != null) {
			if (codeSystemParams.isSnomed()) {
				Matcher idMatcher = SCT_ID_PATTERN.matcher(systemId);
				if (!idMatcher.matches()) {
					throw new FHIROperationException(OperationOutcome.IssueType.CONFLICT, "SNOMED system and id specified but id does not match expected format " +
							"sct_[moduleId]_[YYYYMMDD].");
				}
				String moduleFromId = idMatcher.group(1);
				String versionFromId = idMatcher.group(2);
				if (codeSystemParams.getSnomedModule() != null && !codeSystemParams.getSnomedModule().equals(moduleFromId)) {
					throw new FHIROperationException(OperationOutcome.IssueType.CONFLICT, "SNOMED module in system id and uri do not match.");
				}
				if (codeSystemParams.getVersion() != null && !codeSystemParams.getVersion().equals(versionFromId)) {
					throw new FHIROperationException(OperationOutcome.IssueType.CONFLICT, "SNOMED version in system id and uri do not match.");
				}
				// For SNOMED store the parsed module and version, not the id.
				codeSystemParams.setSnomedModule(moduleFromId);
				codeSystemParams.setVersion(versionFromId);
			} else {
				codeSystemParams.setId(systemId);
			}
		}

		return codeSystemParams;
	}

	public boolean hasUsageContext(MetadataResource r, TokenParam context) {
		if (r.getUseContext() != null && !r.getUseContext().isEmpty()) {
			return r.getUseContext().stream()
				.anyMatch(u -> codingMatches(u.getCode(), context.getValue()));
		}
		return false;
	}

	public boolean hasJurisdiction(MetadataResource r, StringParam jurisdiction) {
		if (r.getJurisdiction() != null && !r.getJurisdiction().isEmpty()) {
			for (CodeableConcept codeableConcept : r.getJurisdiction()) {
				for (Coding c : codeableConcept.getCoding()) {
					if (c.getCode().equals(jurisdiction.getValue())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean hasIdentifier(ValueSet vs, StringParam identifier) {
		if (vs.getIdentifier() != null && !vs.getIdentifier().isEmpty()) {
			for (Identifier i : vs.getIdentifier()) {
				if (stringMatches(i.getValue(), identifier)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasIdentifier(CodeSystem cs, StringParam identifier) {
		if (cs.getIdentifier() != null && !cs.getIdentifier().isEmpty()) {
			for (Identifier i : cs.getIdentifier()) {
				if (stringMatches(i.getValue(), identifier)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean objectMatches(Object obj, StringParam searchTerm) {
		if (searchTerm == null) {
			return true;
		}
		//If we've specified a search term but the target element is not populated, that's not a match
		if (obj == null) {
			return false;
		}
		String value = obj.toString();
		if (obj instanceof Date) {
			value = sdf.format((Date)obj);
		}
		return stringMatches(value, searchTerm);
	}

	public boolean stringMatches(String value, StringParam searchTerm) {
		//If we've not specified a search term, then we pass through a match
		if (searchTerm == null || StringUtils.isEmpty(searchTerm.getValue())) {
			return true;
		}

		//If we've specified a search term but the target element is not populated, that's not a match
		if (value == null) {
			return false;
		}

		//What sort of matching are we doing?  StartsWith by default
		if (searchTerm.isExact()) {
			return value.equalsIgnoreCase(searchTerm.getValue());
		} else if (searchTerm.isContains()) {
			return value.toLowerCase().contains(searchTerm.getValue().toLowerCase());
		} else {
			return value.toLowerCase().startsWith(searchTerm.getValue().toLowerCase());
		}
	}

	public boolean enumerationMatches(Object value, Object search) {
		//If we've specified a search term but the target element is not populated, that's not a match
		if (search != null && value == null) {
			return false;
		}

		//If we've not specified a search term, then we pass through a match
		if (search == null) {
			return true;
		}

		return value.equals(search);
	}


	private boolean codingMatches(Coding c, String string) {
		//If we've specified a search but the target element is not populated, that's not a match
		if (string != null && (c == null || c.getCode() == null)) {
			return false;
		}

		//If we've not specified a search term, then we pass through a match
		if (string == null) {
			return true;
		}

		return c.getCode().equals(string);
	}

	public void setFhirContext(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

	public FhirContext getFhirContext() {
		return fhirContext;
	}

	public static String toString(StringType string) {
		return string != null ? string.getValue() : null;
	}
}
