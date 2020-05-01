package org.snomed.snowstorm.fhir.services;

import java.text.ParseException;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UsageContext;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.*;
import org.apache.commons.lang.StringUtils;
import org.fhir.ucum.TokenType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.BranchPath;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODE;

@Component
public class FHIRHelper implements FHIRConstants {

	private static final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private DialectConfigurationService dialectService;

	@Autowired
	private FHIRValueSetProvider vsService;

	private static final Logger logger = LoggerFactory.getLogger(FHIRHelper.class);

	public static final String UNVERSIONED_STR = "UNVERSIONED";
	public static final int UNVERSIONED = -1;

	public static Integer getSnomedVersion(String versionStr) throws FHIROperationException {
		String versionUri = "/" + FHIRConstants.VERSION + "/";
		if (versionStr.contains(UNVERSIONED_STR)) {
			return UNVERSIONED;
		}

		try {
			return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
					? null
					: Integer.parseInt(versionStr.substring(versionStr.indexOf(versionUri) + versionUri.length()));
		} catch (NumberFormatException e) {
			throw new FHIROperationException(IssueType.CONFLICT, "Version expected to be numeric in format YYYYMMDD" );
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

	String getSnomedEditionModule(StringType versionStr) {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() || versionStr.getValueAsString().equals(FHIRConstants.SNOMED_URI)) {
			return Concepts.CORE_MODULE;
		}
		return getSnomedEditionModule(versionStr.getValueAsString());
	}

	private String getSnomedEditionModule(String versionStr) {
		if (!versionStr.startsWith(FHIRConstants.SNOMED_URI)) {
			throw new NotFoundException("Unknown system URI: " + versionStr + ", expected " + FHIRConstants.SNOMED_URI + "...");
		}
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1,  FHIRConstants.SNOMED_URI.length() + versionStr.length() - FHIRConstants.SNOMED_URI.length())
				: versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1, versionStr.indexOf("/" + FHIRConstants.VERSION + "/"));
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
			String msg = String.format("No code system known with default module of %s.", defaultModule);
			throw new FHIROperationException(IssueType.NOTFOUND, msg );
		}

		CodeSystemVersion codeSystemVersion;
		String shortName = codeSystem.getShortName();
		if (version != null) {
			// Lookup specific version, or the "daily build" branch if we detect "UNVERSIONED" as the version
			if (version.equals(UNVERSIONED)) {
				branchPathStr = codeSystem.getBranchPath();
				logger.debug("Request to use unversioned content, using daily build branchpath: " + branchPathStr);
			} else {
				codeSystemVersion = codeSystemService.findVersion(shortName, version);
				if (codeSystemVersion == null) {
					String msg = String.format("No branch found for Code system %s with edition version %s.", shortName, version);
					throw new FHIROperationException(IssueType.NOTFOUND, msg );
				}
				branchPathStr = codeSystemVersion.getBranchPath();
			}
		} else {
			// Lookup latest published effective version
			branchPathStr = codeSystem.getLatestVersion().getBranchPath();
		}

		if (branchPathStr == null) {
			String msg = String.format("No branch found for Code system %s with default module %s.", shortName, defaultModule);
			throw new FHIROperationException(IssueType.NOTFOUND, msg);
		}
		return new BranchPath(branchPathStr);
	}

	public List<LanguageDialect> getLanguageDialects(List<String> designations, HttpServletRequest request) throws FHIROperationException {
		// Use designations preferably, or fall back to language headers
		if (designations != null) {
			List<LanguageDialect> languageDialects = new ArrayList<>();
			for (String designation : designations) {
				if (designation.length() > MAX_LANGUAGE_CODE_LENGTH) {
					//in this case we're expecting a designation token
					//of the form snomed PIPE langrefsetId
					String[] tokenParts = designation.split(PIPE);
					if (tokenParts.length < 2 ||
							!StringUtils.isNumeric(tokenParts[1]) ||
							!tokenParts[0].equals(SNOMED_URI)) {
						throw new FHIROperationException(IssueType.VALUE, "Malformed designation token '" + designation + "' expected format http://snomed.info/sct PIPE langrefsetId");
					}
					LanguageDialect languageDialect = new LanguageDialect(null, Long.parseLong(tokenParts[1]));
					if (!languageDialects.contains(languageDialect)) {
						languageDialects.add(languageDialect);
					}
				} else {
					languageDialects.add(dialectService.getLanguageDialect(designation));
				}
			}
			if (languageDialects.isEmpty()) {
				languageDialects.add(new LanguageDialect(DEFAULT_LANGUAGE_CODE));
			}
			return languageDialects;
		} else {
			if (request.getHeader(ACCEPT_LANGUAGE_HEADER) == null) {
				return Collections.singletonList(new LanguageDialect(DEFAULT_LANGUAGE_CODE));
			}
			return ControllerHelper.parseAcceptLanguageHeader(request.getHeader(ACCEPT_LANGUAGE_HEADER));
		}
	}

	public String convertToECL(ConceptSetComponent setDefn) throws FHIROperationException {
		String ecl = "";
		boolean firstItem = true;
		for (ConceptReferenceComponent concept : setDefn.getConcept()) {
			if (firstItem) {
				firstItem = false;
			} else {
				ecl += " OR ";
			}
			ecl += concept.getCode() + "|" + concept.getDisplay() + "|";
		}

		for (ConceptSetFilterComponent filter : setDefn.getFilter()) {
			if (firstItem) {
				firstItem = false;
			} else {
				ecl += " AND ";
			}
			if (filter.getProperty().equals("concept")) {
				ecl += convertOperationToECL(filter.getOp());
				ecl += filter.getValue();
			} else if (filter.getProperty().equals("constraint")) {
				if (filter.getOp().toCode() != "=") {
					throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter 'constaint' operation - only '=' currently implemented");
				}
				ecl += filter.getValue();
			} else {
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter property - only 'concept' and 'constraint' currently implemented");
			}

		}

		return ecl;
	}

	private String convertOperationToECL(FilterOperator op) throws FHIROperationException {
		switch (op.toCode()) {
			case "is-a" : return " << ";
			case "=" : return " ";
			case "descendant-of" : return " < ";
			default :
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "ValueSet compose filter operation " + op.toCode() + " (" + op.getDisplay() + ") not currently supported");
		}
	}

	public void ensurePresent(String langCode, List<LanguageDialect> languageDialects) {
		if (languageDialects == null) {
			languageDialects = new ArrayList<>();
		}

		if (langCode != null && !isPresent(languageDialects, langCode)) {
			languageDialects.add(new LanguageDialect(langCode));
		}
	}

	private boolean isPresent(List<LanguageDialect> languageDialects, String langCode) {
		return languageDialects.stream()
				.anyMatch(ld -> ld.getLanguageCode().equals(langCode));
	}

	public String getFirstLanguageSpecified(List<LanguageDialect> languageDialects) {
		for (LanguageDialect dialect : languageDialects) {
			if (dialect.getLanguageCode() != null) {
				return dialect.getLanguageCode();
			}
		}
		return null;
	}

	public boolean expansionContainsCode(QueryService queryService, ValueSet vs, String code) throws FHIROperationException {
		String vsEcl = vsService.covertComposeToEcl(vs.getCompose());
		String filteredEcl = code + " AND (" + vsEcl + ")";
		BranchPath branchPath = getBranchPathFromURI(null);
		Page<ConceptMini> concepts = eclSearch(queryService, filteredEcl, true, null, null, branchPath, 0, NOT_SET);
		return concepts.getSize() > 0;
	}

	public void append(StringType str, String appendMe) {
		str.setValueAsString(str.toString() + appendMe);
	}

	public void requireOneOf(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 == null && param2 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, "One of '" + param1Name + "' or '" + param2Name + "' parameters must be supplied");
		}
	}

	public void mutuallyExclusive(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 != null && param2 != null) {
			throw new FHIROperationException(IssueType.INVARIANT, "Use one of '" + param1Name + "' or '" + param2Name + "' parameters");
		}
	}

	public void mutuallyRequired(String param1Name, Object param1, String param2Name, Object param2) throws FHIROperationException {
		if (param1 != null && param2 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, "Input parameter '" + param1Name + "' can only be used in conjunction with parameter '" + param2Name);
		}
	}

	public void mutuallyRequired(String param1Name, Object param1, String param2Name, Object param2, String param3Name, Object param3) throws FHIROperationException {
		if (param1 != null && param2 == null && param3 == null) {
			throw new FHIROperationException(IssueType.INVARIANT, "Use of input parameter '" + param1Name + "' only allowed if '" + param2Name + "' or '" + param3Name + "' is also present");
		}
	}

	public void notSupported(String paramName, Object obj) throws FHIROperationException {
		if (obj != null) {
			throw new FHIROperationException(IssueType.NOTSUPPORTED, "Input parameter '" + paramName + "' is not currently supported.");
		}
	}

	public String recoverConceptId(CodeType code, Coding coding) throws FHIROperationException {
		String conceptId = null;
		if (code == null && coding == null) {
			throw new FHIROperationException(IssueType.INVARIANT, "Use one of 'code' or 'coding' parameters");
		} else if (code != null && coding == null) {
			if (code.getCode().contains("|")) {
				throw new FHIROperationException(IssueType.NOTSUPPORTED, "'code' parameter cannot supply a codeSystem. Use 'coding' or provide CodeSystem in 'system' parameter");
			}
			conceptId = code.getCode();
			if (!StringUtils.isNumeric(conceptId)) {
				throw new FHIROperationException(IssueType.NOTSUPPORTED, "Only numeric SNOMED CT identifiers are currently supported");
			}
		} else if (code == null && coding != null) {
			if (coding.getSystem() != null && !coding.getSystem().startsWith(SNOMED_URI)) {
				throw new FHIROperationException(IssueType.NOTSUPPORTED, "CodeSystem of 'coding' must be based on" + SNOMED_URI);
			}
			conceptId = coding.getCode();
		} else {
			//Can only handle one of the two
			throw new FHIROperationException(IssueType.INVARIANT, "Use either 'code' or 'coding' parameters, not both");
		}
		if (!IdentifierService.isConceptId(conceptId)) {
			throw new FHIROperationException(IssueType.CODEINVALID, conceptId + " is not even a SNOMED CT code.");
		}
		return conceptId;
	}

	public StringType enhanceCodeSystem (StringType codeSystem, StringType version, Coding coding) throws FHIROperationException {

		if (codeSystem != null) {
			if (!codeSystem.asStringValue().equals(SNOMED_URI)) {
				throw new FHIROperationException(IssueType.VALUE, "Snowstorm FHIR API currently only accepts '" + SNOMED_URI + "' as a (code)system.  Additionally, the version parameter can be used to specify a module and effective date");
			}
		} else {
			codeSystem = new StringType (SNOMED_URI);
		}

		if (version != null) {
			if (!version.asStringValue().startsWith(codeSystem.asStringValue())) {
				throw new FHIROperationException(IssueType.CONFLICT, "Version parameter must start with '" + SNOMED_URI + "'");
			}
			getSnomedVersion(version.asStringValue());
			codeSystem = version;
		}

		if (coding != null && coding.getSystem() != null) {
			String codeSystemFromCoding = coding.getSystem();
			//We're OK if the code system is the default and the system in the coding is more specific
			if (codeSystem.toString().equals(SNOMED_URI) && codeSystemFromCoding.toString().length() > SNOMED_URI.length()) {
				//This is fine, use the coding
				codeSystem = new StringType(codeSystemFromCoding);
			} else if (!codeSystem.toString().equals(codeSystemFromCoding)) {
				throw new FHIROperationException(IssueType.CONFLICT, "CodeSystem defined in (code)system paramter + version is not identical to that supplied in the coding parameter");
			}
		}
		return codeSystem;
	}

	public Page<ConceptMini> eclSearch(QueryService queryService, String ecl, Boolean active, String termFilter, List<LanguageDialect> languageDialects, BranchPath branchPath, int offset, int pageSize) {
		Page<ConceptMini> conceptMiniPage;
		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false);  //Inferred view only for now
		queryBuilder.ecl(ecl)
				.descriptionCriteria(descriptionCriteria -> descriptionCriteria
						.term(termFilter)
						.searchLanguageCodes(LanguageDialect.toLanguageCodes(languageDialects)))
				.resultLanguageDialects(languageDialects)
				.activeFilter(active);
		conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.decodePath(branchPath.toString()), PageRequest.of(offset, pageSize));
		return conceptMiniPage;
	}

	public boolean hasUsageContext(ValueSet vs, TokenParam context) {
		if (vs.getUseContext() != null && vs.getUseContext().size() > 0) {
			return vs.getUseContext().stream()
				.anyMatch(u -> codingMatches(u.getCode(), context.getValue()));
		}
		return false;
	}

	public boolean hasJurisdiction(ValueSet vs, StringParam jurisdiction) {
		if (vs.getJurisdiction() != null && vs.getJurisdiction().size() > 0) {
			for (CodeableConcept codeableConcept : vs.getJurisdiction()) {
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
		if (vs.getIdentifier() != null && vs.getIdentifier().size() > 0) {
			for (Identifier i : vs.getIdentifier()) {
				if (stringMatches(i.getValue(), identifier)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean stringMatches(String value, StringParam searchTerm) {
		//If we've not specified a search term, then we pass through a match
		if (searchTerm == null || StringUtils.isEmpty(searchTerm.getValue())) {
			return true;
		}

		//If we've specified a search term but the target element is not populated, that's not a match
		if (searchTerm.getValue() != null && value == null) {
			return false;
		}

		//What sort of matching are we doing?  StartsWith by default
		if (searchTerm.isExact()) {
			return value.toLowerCase().equals(searchTerm.getValue().toLowerCase());
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

	public static void validateEffectiveTime (String input) throws org.snomed.snowstorm.fhir.services.FHIROperationException {
		if (!StringUtils.isEmpty(input)) {
			try {
				sdf.parse(input.trim());
			} catch (ParseException e) {
				throw new FHIROperationException(IssueType.VALUE, "Version is expected to be in format YYYYMMDD only.  Instead received: " + input);
			}
		}
	}

}
