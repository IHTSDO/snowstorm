package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.rest.View;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TermValidationServiceClient {

	public static final String DUPLICATE_RULE_ID = "21c099d0-594f-4473-bc46-d5701fcfac0e";
	public static final String SIMILAR_TO_INACTIVE_RULE_ID = "fe9a5f26-d9e5-493c-a0c5-0206d4a036a1";

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
			logger.info("Calling term-validation-service for branch {}", branchPath);
			final HttpEntity<ValidationRequest> request = new HttpEntity<>(validationRequest, HTTP_HEADERS);
			final ResponseEntity<ValidationResponse> response = restTemplate.postForEntity("/validate-concept",
					request, ValidationResponse.class);

			final ValidationResponse validationResponse = response.getBody();
			handleResponse(validationResponse, concept, branchPath, invalidContents);
		} catch (HttpStatusCodeException e) {
			try {
				logger.info("Request failed, request body: {}", mapper.writeValueAsString(validationRequest));
			} catch (JsonProcessingException jsonProcessingException) {
				logger.error("Request failed but also error serialising the object for the error message!", e);
			}
			throw new ServiceException(String.format("Call to term-validation-service was not successful: %s, %s", e.getStatusCode(), e.getMessage()));
		} finally {
			termValidation.checkpoint("Validation");
			termValidation.finish();
		}

		return invalidContents;
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
					invalidContents.add(new InvalidContent(DUPLICATE_RULE_ID, new DroolsConcept(concept),
							String.format("Terms are similar to description '%s' in concept %s. Is this a duplicate?", match.getTerm(), match.getConceptId()),
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
							String.format("Concept is similar to many that have been made inactive in the past with inactivation reason '%s'.",	inactivationReason),
							Severity.WARNING));
				} else {
					logger.warn("TVS indicated that future concept inactivation likely but gave no details so nothing shown to user. Branch {}, concept {}",
							branchPath, concept.getConceptId());
				}
			}
		}
	}

	public ObjectMapper getObjectMapper() {
		return mapper;
	}

	public static final class ValidationRequest {

		private final String status;
		private final Concept concept;

		public ValidationRequest(Concept concept, boolean afterClassification) {
			status = afterClassification ? "post-classification" : "on-save";
			this.concept = concept;
		}

		@JsonView(View.Component.class)
		public String getStatus() {
			return status;
		}

		@JsonView(View.Component.class)
		public Concept getConcept() {
			return concept;
		}

	}

	public static final class ValidationResponse {

		// Detect duplicate concepts
		private Duplication duplication;

		// Measure similarity to previously inactivated concepts
		private ModelPrediction modelPrediction;

		public Duplication getDuplication() {
			return duplication;
		}

		public ModelPrediction getModelPrediction() {
			return modelPrediction;
		}

		public void setModelPrediction(ModelPrediction modelPrediction) {
			this.modelPrediction = modelPrediction;
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
}
