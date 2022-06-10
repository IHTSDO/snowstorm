package org.snomed.snowstorm.core.data.services;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.WebRouterConfigurationService.WebRoute;
import org.snomed.snowstorm.core.data.services.identifier.VerhoeffCheck;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

@Service
public class WebRoutingService {
	
	public static String CANONICAL_URI_PREFIX = "http://snomed.info/";
	
	@Value("${uri.dereferencing.prefix}")
	private String hostedPrefix;
	
	private static int NS_LEN = 7;
	private static int PTN_CHK_LEN = 3;
	private static String DEFAULT_NAMESPACE = "default";
	
	@Autowired
	private WebRouterConfigurationService webRoutes;
	
	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private MultiSearchService multiSearchService;

	public String determineRedirectionString(String uri, String acceptHeader, String _format) {
		UriParts uriParts = parse(uri);
		WebRoute webRoute = webRoutes.lookup(uriParts.nameSpace, acceptHeader, _format);
		
		//TODO Add support for xsct / xid to route to unpublished data
		CodeSystemVersion version = determineVersion(uriParts);
		
		//If we managed to determine how to route this request, is this concept on our server?
		MutableBoolean versionConceptFound = new MutableBoolean();
		Concept concept = findConcept(uriParts, version, versionConceptFound);
		
		return populateWebRouteTemplate(concept, uriParts, webRoute, version, versionConceptFound);
	}
	
	private String populateWebRouteTemplate(Concept concept, UriParts uriParts, WebRoute webRoute, CodeSystemVersion version, MutableBoolean versionConceptFound) {
		//If we tried a local lookup and can't find the concept then try to redirect according to the 
		//namespace, or fall-back
		if (concept == null && (webRoute == null || webRoute.isDefaultRoute()) && uriParts.nameSpace != null) {
			return populateWebRouteTemplate(null, uriParts, webRoutes.getFallbackRoute(), version, versionConceptFound);
		}

		if (webRoute == null) {
			webRoute = webRoutes.getFallbackRoute();
		}

		String template = webRoute.getRedirectionTemplate();
		if (template.contains("{BRANCH}")) {
			if (concept == null) {
				throw new IllegalArgumentException("URI Redirection needed to find a BRANCH, but no concept was found");
			}

			if (Boolean.TRUE.equals(versionConceptFound.getValue())) {
				template = template.replace("{BRANCH}", version.getBranchPath());
			} else {
				template = template.replace("{BRANCH}", concept.getPath());
			}
		}
		
		if (template.contains("{SCTID}")) {
			template = template.replace("{SCTID}", uriParts.sctId);
		}
		
		if (template.contains("{VERSION_URI}")) {
			String versionUri = CANONICAL_URI_PREFIX + "sct/";
			if (uriParts.moduleId != null) {
				versionUri += uriParts.moduleId;
			} else if (concept != null) {
				//TODO Watch what happens when we have the model module rather than 
				//the default module for the branch.
				versionUri += concept.getModuleId();
			}
			if (uriParts.effectiveDate != null) {
				versionUri += "/version/" + uriParts.effectiveDate;
			}
			template = template.replace("{VERSION_URI}", versionUri);
		}
		return template;
	}

	public String extractNamespace(String sctId) {
		int len = sctId.length();
		//Is this a core concept?
		if (sctId.charAt(len - 3) == '0') {
			return DEFAULT_NAMESPACE;
		} else {
			int from = len - NS_LEN - PTN_CHK_LEN;
			int to = len - PTN_CHK_LEN;
			return sctId.substring(from, to);
		}
	}

	private CodeSystemVersion determineVersion(UriParts uriParts) {
		CodeSystem codeSystem = codeSystemService.findByDefaultModule(uriParts.moduleId);
		if (codeSystem == null) {
			//What's the default code system on MAIN then, get it's latest version?
			String shortName = codeSystemService.findByBranchPath("MAIN").get().getShortName();
			return codeSystemService.findLatestVisibleVersion(shortName);
		}
		
		if (uriParts.effectiveDate == null) {
			return codeSystemService.findLatestVisibleVersion(codeSystem.getShortName());
		} else {
			return codeSystemService.findVersion(codeSystem.getShortName(), uriParts.effectiveDate);
		}
	}

	private Concept findConcept(UriParts uriParts, CodeSystemVersion version, MutableBoolean versionedConceptFound) {
		//Multisearch is expensive, so we'll try on default branch for the specified module/version first
		Concept concept = null;
		
		if (version != null) {
			concept = conceptService.find(uriParts.sctId, null, version.getBranchPath());
			if (concept != null) {
				versionedConceptFound.setTrue();
				//Ensure we're redirecting to a published version 
				concept.setPath(multiSearchService.getPublishedVersionOfBranch(concept.getPath()));
			}
		}
		
		if (concept == null) {
			ConceptCriteria criteria = new ConceptCriteria().conceptIds(Collections.singleton(uriParts.sctId));
			Page<Concept> concepts = multiSearchService.findConcepts(criteria, PageRequest.of(0, 1));
			List<Concept> content = concepts.getContent();
			if (!content.isEmpty()) {
				concept = content.get(0);
			}
		}
		return concept;
	}

	private UriParts parse (String uri) {
		UriParts parts = new UriParts();
		if (!uri.startsWith(hostedPrefix)) {
			throw new IllegalArgumentException(uri + " URI did not start with expected '" + hostedPrefix + "'");
		}
		String uriTrimmed = uri.substring(hostedPrefix.length());
		//If we get given a code system version URI, we can just say the module and the SCTID are one and the same
		String[] uriSplit = uriTrimmed.split("\\/");
		if (uriSplit.length < 2) {
			throw new IllegalArgumentException("Malformed URI: " + uri);
		} else if (uriTrimmed.startsWith("sct/")) {
			parts.moduleId = uriSplit[1];
			//If we just have the module id, look that up
			parts.sctId = parts.moduleId;
			//But if we find "id" then the next field is the sctid
			if (uriSplit.length >= 4) {
				if (uriSplit[2].equals("id")) {
					parts.sctId = uriSplit[3];
				} else if (uriSplit[2].equals("version")) {
					try {
						parts.effectiveDate = Integer.parseInt(uriSplit[3]);
					} catch (Exception e) {
						throw new IllegalArgumentException(uri + " version part was not an integer");
					}
				}
				if (uriSplit.length == 6 && uriSplit[4].equals("id")) {
					parts.sctId = uriSplit[5];
				}
			}
		} else if (uriTrimmed.startsWith("id/")) {
			parts.sctId = uriSplit[1];
			if (uriSplit.length >= 3) {
				parts.moduleId = uriSplit[2];
			}
			if (uriSplit.length == 5 && uriSplit[3].equals("version")) {
				try {
					parts.effectiveDate = Integer.parseInt(uriSplit[4]);
				} catch (Exception e) {
					throw new IllegalArgumentException(uri + " version part was not an integer");
				}
			}
		} else {
			throw new IllegalArgumentException(uri + " URI did not indicate sct or id");
		}
		
		//Validate the component id
		//TODO Looking up a UUID for a refset member is in the specification
		if (!parts.sctId.matches("(0|[1-9]\\d*)") ||
				!VerhoeffCheck.validateLastChecksumDigit(parts.sctId)) {
			throw new IllegalArgumentException("URI featured invalid SCTID: " + uri);
		}
		
		parts.nameSpace = extractNamespace(parts.sctId);
		return parts;
	}

	private class UriParts {
		String nameSpace;
		String sctId;
		String moduleId;
		Integer effectiveDate;
		
		@Override
		public String toString() {
			return "[" 
				+ "Namespace: " + nameSpace
				+ ", sctId: " + sctId
				+ ", moduleId: " + moduleId
				+ ", effectiveDate: " + effectiveDate
				+ "]";
		}
	}
}
