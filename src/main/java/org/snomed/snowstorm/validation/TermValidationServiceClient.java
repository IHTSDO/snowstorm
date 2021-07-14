package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TermValidationServiceClient {

	public static final String DUPLICATE_RULE_ID = "21c099d0-594f-4473-bc46-d5701fcfac0e";

	private static final HttpHeaders HTTP_HEADERS = new HttpHeaders();
	static {
		HTTP_HEADERS.setContentType(MediaType.APPLICATION_JSON);
	}

	private final float duplicateScoreThreshold;

	private RestTemplate restTemplate;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TermValidationServiceClient(@Value("${term-validation-service.url}") String termValidationServiceUrl,
			@Value("${term-validation-service.scoreThreshold.duplicate}") float duplicateScoreThreshold) {

		if (!StringUtils.isEmpty(termValidationServiceUrl)) {
			this.restTemplate = new RestTemplateBuilder()
					.rootUri(termValidationServiceUrl)
					.messageConverters(new MappingJackson2HttpMessageConverter())
					.build();
		}
		this.duplicateScoreThreshold = duplicateScoreThreshold;
	}

	public List<InvalidContent> validateConcept(String branchPath, Concept concept, boolean afterClassification) throws ServiceException {
		final ArrayList<InvalidContent> invalidContents = new ArrayList<>();
		final TimerUtil termValidation = new TimerUtil("term-validation");

		if (restTemplate == null) {
			return invalidContents;
		}

		// Populate linked concept information
		Map<String, ConceptMini> relationshipConceptMinis = new HashMap<>();
		final List<LanguageDialect> languageDialects = Config.DEFAULT_LANGUAGE_DIALECTS;
		concept.getClassAndGciAxioms().stream().flatMap(axiom -> axiom.getRelationships().stream()).forEach(
				relationship -> relationship.createConceptMinis(languageDialects, relationshipConceptMinis));
		concept.getRelationships().forEach(
				relationship -> relationship.createConceptMinis(languageDialects, relationshipConceptMinis));

		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		conceptService.populateConceptMinis(branchCriteria, relationshipConceptMinis, languageDialects);
		termValidation.checkpoint("Populate linked concept details.");

		try {
			final ValidationRequest validationRequest = new ValidationRequest(concept);
			if (afterClassification) {
				final Long conceptIdAsLong = concept.getConceptIdAsLong();
				final Set<Long> parentIds = queryService.findParentIds(branchCriteria, false, Collections.singleton(conceptIdAsLong));
				final Set<Long> grandParentIds = queryService.findParentIds(branchCriteria, false, parentIds);
				final Set<Long> ancestorIds = queryService.findAncestorIds(branchCriteria, branchPath, false, concept.getConceptId());
				final Set<Long> siblingIds = queryService.findChildrenIdsAsUnion(branchCriteria, false, parentIds);
				final Set<Long> childrenIds = queryService.findChildrenIdsAsUnion(branchCriteria, false, Collections.singleton(conceptIdAsLong));
				validationRequest.addConceptGraphCounts(conceptIdAsLong,
						new GraphCounts(parentIds.size(), grandParentIds.size(), ancestorIds.size(), siblingIds.size(), childrenIds.size()));
				termValidation.checkpoint("Gather graph counts");
			}

			logger.info("Calling term-validation-service for branch {}", branchPath);
			final ResponseEntity<ValidationResponse> response = restTemplate.postForEntity("/validate-concept",
					new HttpEntity<>(validationRequest, HTTP_HEADERS), ValidationResponse.class);

			final ValidationResponse validationResponse = response.getBody();
			if (validationResponse != null) {
				final Optional<Match> first = validationResponse.getDuplication().getMatches().stream()
						.filter(match -> match.getScore() > duplicateScoreThreshold && (concept.getConceptId().contains("-") || !match.getConceptId().equals(concept.getConceptIdAsLong())))
						.findFirst();
				if (first.isPresent()) {
					final Match match = first.get();
					invalidContents.add(new InvalidContent(DUPLICATE_RULE_ID, new DroolsConcept(concept),
							String.format("Terms are similar to description '%s' in concept %s. Is this a duplicate?", match.getTerm(), match.getConceptId()),
							Severity.WARNING));
				}
			}
		} catch (HttpClientErrorException e) {
			throw new ServiceException(String.format("Call to term-validation-service was not successful: %s, %s", e.getStatusCode(), e.getMessage()));
		} finally {
			termValidation.checkpoint("Validation");
			termValidation.finish();
		}

		return invalidContents;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public static final class ValidationRequest {

		private final String status;
		private final Concept concept;
		private Map<Long, GraphCounts> conceptGraphCounts;

		public ValidationRequest(Concept concept) {
			status = "on-save";
			this.concept = concept;
		}

		public void addConceptGraphCounts(Long conceptId, GraphCounts graphCounts) {
			if (conceptGraphCounts == null) {
				conceptGraphCounts = new HashMap<>();
			}
			conceptGraphCounts.put(conceptId, graphCounts);
		}

		public String getStatus() {
			return status;
		}

		public Concept getConcept() {
			return concept;
		}

		public Map<Long, GraphCounts> getConceptGraphCounts() {
			return conceptGraphCounts;
		}
	}

	public static final class GraphCounts {

		private final int parentsCount;
		private final int grandparentsCount;
		private final int ancestorsCount;
		private final int siblingsCount;
		private final int childrenCount;

		public GraphCounts(int parentsCount, int grandparentsCount, int ancestorsCount, int siblingsCount, int childrenCount) {
			this.parentsCount = parentsCount;
			this.grandparentsCount = grandparentsCount;
			this.ancestorsCount = ancestorsCount;
			this.siblingsCount = siblingsCount;
			this.childrenCount = childrenCount;
		}

		public int getParentsCount() {
			return parentsCount;
		}

		public int getGrandparentsCount() {
			return grandparentsCount;
		}

		public int getAncestorsCount() {
			return ancestorsCount;
		}

		public int getSiblingsCount() {
			return siblingsCount;
		}

		public int getChildrenCount() {
			return childrenCount;
		}
	}

	public static final class ValidationResponse {

		private Duplication duplication;

		public Duplication getDuplication() {
			return duplication;
		}
	}

	public static final class Duplication {

		private List<Match> matches;

		public List<Match> getMatches() {
			return matches;
		}
	}

	public static final class Match {

		private Long conceptId;
		private String term;
		private Long typeId;
		private Float score;

		public Long getConceptId() {
			return conceptId;
		}

		public String getTerm() {
			return term;
		}

		public Long getTypeId() {
			return typeId;
		}

		public Float getScore() {
			return score;
		}
	}
}
