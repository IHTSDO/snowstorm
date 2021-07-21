package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.rest.View;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class TermValidationServiceClient {

	public static final String DUPLICATE_RULE_ID = "21c099d0-594f-4473-bc46-d5701fcfac0e";
	public static final String SIMILAR_TO_INACTIVE_RULE_ID = "fe9a5f26-d9e5-493c-a0c5-0206d4a036a1";
	public static final String FSN_COVERAGE_RULE_ID = "083b4a5f-6e01-4b78-a37f-11ebaaf8efca";
	public static final String LOCAL_CONTEXT_RULE_ID = "6a355a4e-9855-4681-9b23-48b02958dbb3";

	private static final HttpHeaders HTTP_HEADERS = new HttpHeaders();

	static {
		HTTP_HEADERS.setContentType(MediaType.APPLICATION_JSON);
	}

	private final float duplicateScoreThreshold;

	private RestTemplate restTemplate;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryService queryService;

	private final ObjectMapper mapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TermValidationServiceClient(@Value("${term-validation-service.url}") String termValidationServiceUrl,
			@Value("${term-validation-service.scoreThreshold.duplicate}") float duplicateScoreThreshold) {

		mapper = new ObjectMapper()
				.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.setConfig(mapper.getSerializationConfig().withView(View.Component.class));

		if (!StringUtils.isEmpty(termValidationServiceUrl)) {
			this.restTemplate = new RestTemplateBuilder()
					.rootUri(termValidationServiceUrl)
					.messageConverters(new MappingJackson2HttpMessageConverter(mapper))
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

		final ValidationRequest validationRequest = new ValidationRequest(concept, afterClassification);
		try {
			final HttpEntity<ValidationRequest> request = new HttpEntity<>(validationRequest, HTTP_HEADERS);
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

			if (logger.isDebugEnabled()) {
				logger.debug("Calling term-validation-service for branch {}, request body: {}", branchPath, getRequestBodyOrLogError(validationRequest));
			} else {
				logger.info("Calling term-validation-service for branch {}", branchPath);
			}

			final ResponseEntity<ValidationResponse> response = restTemplate.postForEntity("/validate-concept",
					request, ValidationResponse.class);

			final ValidationResponse validationResponse = response.getBody();
			handleResponse(validationResponse, concept, branchPath, invalidContents);
		} catch (HttpStatusCodeException e) {
			logger.info("Request failed, request body: {}", getRequestBodyOrLogError(validationRequest));
			throw new ServiceException(String.format("Call to term-validation-service was not successful: %s, %s", e.getStatusCode(), e.getMessage()));
		} finally {
			termValidation.checkpoint("Validation");
			termValidation.finish();
		}

		return invalidContents;
	}

	private String getRequestBodyOrLogError(ValidationRequest validationRequest) {
		try {
			return mapper.writeValueAsString(validationRequest);
		} catch (JsonProcessingException e) {
			logger.error("Error serialising request.", e);
		}
		return null;
	}

	void handleResponse(ValidationResponse validationResponse, Concept concept, String branchPath, List<InvalidContent> invalidContents) {
		if (validationResponse != null) {
			// Duplicate
			if (validationResponse.getDuplication() != null) {
				final Optional<Match> first = validationResponse.getDuplication().getMatches().stream()
						.filter(match -> match.getScore() > duplicateScoreThreshold && (concept.getConceptId().contains("-") || !match.getConceptId().equals(concept.getConceptIdAsLong())))
						.max(Comparator.comparing(Match::getScore));
				if (first.isPresent()) {
					final Match match = first.get();
					invalidContents.add(new InvalidContent(DUPLICATE_RULE_ID, new DroolsDescription(getEnFsnDescription(concept)),
							String.format("FSN is similar to description '%s' in concept %s. Is this a duplicate?", match.getTerm(), match.getConceptId()),
							Severity.WARNING));
				}
			}

			// Inactivation prediction
			final ModelPrediction modelPrediction = validationResponse.getModelPrediction();
			if (modelPrediction != null && !modelPrediction.isOkay()) {
				final Optional<ModelPredictionDetail> highestScoringDetail = modelPrediction.getHighestScoringDetail();
				if (highestScoringDetail.isPresent()) {
					final String inactivationReason = highestScoringDetail.get().getType();
					invalidContents.add(new InvalidContent(SIMILAR_TO_INACTIVE_RULE_ID, new DroolsConcept(concept),
							String.format("This concept is similar to many that have been made inactive in the past with inactivation reason '%s'.",	inactivationReason),
							Severity.WARNING));
				} else {
					logger.warn("TVS indicated that future concept inactivation likely but gave no details so nothing shown to user. Branch {}, concept {}",
							branchPath, concept.getConceptId());
				}
			}

			// FSN coverage
			final FsnCoverage fsnCoverage = validationResponse.getFsnCoverage();
			if (fsnCoverage != null) {
				Set<String> wordsNotCovered = fsnCoverage.getWordCoverage().stream()
						.filter(Predicate.not(WordCoverage::isPresentAnywhere))
						.map(WordCoverage::getWord)
						.collect(Collectors.toSet());

				if (!wordsNotCovered.isEmpty()) {
					String commonEnding = "not occur in the descriptions of any concept in the inferred relationships. " +
							"Is the description and modeling correct?";
					if (wordsNotCovered.size() == 1) {
						invalidContents.add(new InvalidContent(FSN_COVERAGE_RULE_ID, new DroolsDescription(getEnFsnDescription(concept)),
								String.format("The word '%s' in the FSN of this defined concept does %s", wordsNotCovered.iterator().next(), commonEnding),
								Severity.WARNING));
					} else {
						String lastWord = Iterables.getLast(wordsNotCovered);
						wordsNotCovered.remove(lastWord);
						String otherWords = wordsNotCovered.stream().map(s -> String.format("'%s'", s)).collect(Collectors.joining(", "));
						invalidContents.add(new InvalidContent(FSN_COVERAGE_RULE_ID, new DroolsDescription(getEnFsnDescription(concept)),
								String.format("The words %s and '%s' in the FSN of this defined concept do %s", otherWords, lastWord, commonEnding),
								Severity.WARNING));
					}
				}
			}

			// Local context
			final LocalContext localContext = validationResponse.getLocalContext();
			if (localContext != null) {
				final Set<LocalContextTermMatch> recommendedSwitches = localContext.getTermMatches().stream()
						.filter(LocalContextTermMatch::isRecommendSwitch)
						.collect(Collectors.toSet());
				for (LocalContextTermMatch recommendedSwitch : recommendedSwitches) {
					final LocalContextTerm baseTerm = recommendedSwitch.getBaseTerm();
					final Optional<Description> descriptionOptional = concept.getDescriptions().stream()
							.filter(description -> description.getTerm().equals(baseTerm.getTerm())).findFirst();
					final LocalContextTerm closestTerm = recommendedSwitch.getClosestTerm();
					final String message = String.format("Description '%s' seems better suited to concept %s, which has description '%s'. " +
									"Perhaps it should be moved or removed?",
							baseTerm.getTerm(), closestTerm.getConceptId(), closestTerm.getTerm());

					if (descriptionOptional.isPresent()) {
						invalidContents.add(new InvalidContent(LOCAL_CONTEXT_RULE_ID, new DroolsDescription(descriptionOptional.get()), message, Severity.WARNING));
					} else {
						invalidContents.add(new InvalidContent(LOCAL_CONTEXT_RULE_ID, new DroolsConcept(concept), message, Severity.WARNING));
					}
				}
			}
		}
	}

	private Description getEnFsnDescription(Concept concept) {
		return concept.getDescriptions().stream().filter(d -> d.getLang().equals("en")).findFirst().orElse(null);
	}

	public ObjectMapper getObjectMapper() {
		return mapper;
	}

	public static final class ValidationRequest {

		private final String status;
		private final Concept concept;
		private Map<Long, GraphCounts> conceptGraphCounts;

		public ValidationRequest(Concept concept, boolean afterClassification) {
			status = afterClassification ? "post-classification" : "on-save";
			this.concept = concept;
		}

		public void addConceptGraphCounts(Long conceptId, GraphCounts graphCounts) {
			if (conceptGraphCounts == null) {
				conceptGraphCounts = new HashMap<>();
			}
			conceptGraphCounts.put(conceptId, graphCounts);
		}

		@JsonView(View.Component.class)
		public String getStatus() {
			return status;
		}

		@JsonView(View.Component.class)
		public Concept getConcept() {
			return concept;
		}

		@JsonView(View.Component.class)
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

		@JsonView(View.Component.class)
		public int getParentsCount() {
			return parentsCount;
		}

		@JsonView(View.Component.class)
		public int getGrandparentsCount() {
			return grandparentsCount;
		}

		@JsonView(View.Component.class)
		public int getAncestorsCount() {
			return ancestorsCount;
		}

		@JsonView(View.Component.class)
		public int getSiblingsCount() {
			return siblingsCount;
		}

		@JsonView(View.Component.class)
		public int getChildrenCount() {
			return childrenCount;
		}
	}

	public static final class ValidationResponse {

		// Detect duplicate concepts
		private Duplication duplication;

		// Measure similarity to previously inactivated concepts
		private ModelPrediction modelPrediction;

		private FsnCoverage fsnCoverage;

		private LocalContext localContext;

		public Duplication getDuplication() {
			return duplication;
		}

		public ModelPrediction getModelPrediction() {
			return modelPrediction;
		}

		public FsnCoverage getFsnCoverage() {
			return fsnCoverage;
		}

		public LocalContext getLocalContext() {
			return localContext;
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

	public static final class ModelPrediction {

		private boolean okay;
		private List<ModelPredictionDetail> detail;

		public Optional<ModelPredictionDetail> getHighestScoringDetail() {
			return detail.stream().max(Comparator.comparing(ModelPredictionDetail::getScore));
		}

		public boolean isOkay() {
			return okay;
		}

		public List<ModelPredictionDetail> getDetail() {
			return detail;
		}
	}

	public static final class ModelPredictionDetail {

		private String type;
		private float score;

		public float getScore() {
			return score;
		}

		public String getType() {
			return type;
		}
	}

	public static final class FsnCoverage {

		private List<WordCoverage> wordCoverage;

		public List<WordCoverage> getWordCoverage() {
			return wordCoverage;
		}
	}

	public static final class WordCoverage {

		private String word;
		private String pos;
		private boolean samePosPresent;
		private boolean presentAnywhere;

		public String getWord() {
			return word;
		}

		public String getPos() {
			return pos;
		}

		public boolean isSamePosPresent() {
			return samePosPresent;
		}

		public boolean isPresentAnywhere() {
			return presentAnywhere;
		}
	}

	public static final class LocalContext {

		private LocalContextTerm fsn;
		private List<LocalContextTermMatch> termMatches;

		public LocalContextTerm getFsn() {
			return fsn;
		}

		public List<LocalContextTermMatch> getTermMatches() {
			return termMatches;
		}
	}

	public static final class LocalContextTerm {

		private String term;
		private String lemmatizedTerm;
		private String tag;
		private List<String> pos;
		private String conceptId;

		public String getTerm() {
			return term;
		}

		public String getLemmatizedTerm() {
			return lemmatizedTerm;
		}

		public String getTag() {
			return tag;
		}

		public List<String> getPos() {
			return pos;
		}

		public String getConceptId() {
			return conceptId;
		}
	}

	public static final class LocalContextTermMatch {

		private LocalContextTerm baseTerm;
		private float averageSynonymMatch;
		private LocalContextTerm closestTerm;
		private boolean recommendSwitch;

		public LocalContextTerm getBaseTerm() {
			return baseTerm;
		}

		public float getAverageSynonymMatch() {
			return averageSynonymMatch;
		}

		public LocalContextTerm getClosestTerm() {
			return closestTerm;
		}

		public boolean isRecommendSwitch() {
			return recommendSwitch;
		}
	}
}
