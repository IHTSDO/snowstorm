package org.snomed.snowstorm.core.data.services.postcoordination;

import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.otf.owltoolkit.normalform.RelationshipChangeProcessor;
import org.snomed.otf.owltoolkit.service.ClassificationContainer;
import org.snomed.otf.owltoolkit.service.ReasonerServiceException;
import org.snomed.otf.owltoolkit.service.SnomedReasonerService;
import org.snomed.otf.owltoolkit.util.InputStreamSet;
import org.snomed.snowstorm.config.SnomedReleaseResourceConfiguration;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

import static java.lang.String.format;
import static org.snomed.otf.owltoolkit.service.SnomedReasonerService.ELK_REASONER_FACTORY;
import static org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService.EXPRESSION_PARTITION_ID;

@Service
public class IncrementalClassificationService {

	private final SnomedReasonerService snomedReasonerService;

	private final ResourceManager releaseResourceManager;

	private final String releaseResourceConfigName;

	private final Map<String, ClassificationContainer> classificationContainers = new HashMap<>();

	@Autowired
	private ExpressionAxiomConversionService expressionAxiomConversionService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public IncrementalClassificationService(@Autowired ResourceLoader cloudResourceLoader, @Autowired SnomedReleaseResourceConfiguration snomedReleaseResourceConfiguration) {
		this.releaseResourceManager = new ResourceManager(snomedReleaseResourceConfiguration, cloudResourceLoader);
		snomedReasonerService = new SnomedReasonerService();
		releaseResourceConfigName = SnomedReleaseResourceConfiguration.class.getAnnotation(ConfigurationProperties.class).value();
	}

	public void classify(Map<PostCoordinatedExpression, ComparableExpression> classifiableFormsMap, boolean equivalenceTest, String classificationPackage)
			throws ServiceException {

		// Transform to axiom(s)
		Set<AxiomRepresentation> axioms = new HashSet<>();
		for (ComparableExpression classifiableForm : classifiableFormsMap.values()) {
			axioms.addAll(expressionAxiomConversionService.convertToAxioms(classifiableForm));
		}

		// Classify
		final RelationshipChangeProcessor changeProcessor;
		try {
			changeProcessor = classifyTransientAxioms(axioms, equivalenceTest, classificationPackage);
		} catch (IOException | ReasonerServiceException e) {
			throw new ServiceException("Failed to classify expression.", e);
		}

		Map<Long, Set<Long>> equivalentConceptMap = getEquivalentConceptMap(changeProcessor.getEquivalentConceptIds(), otherId -> !otherId.startsWith(EXPRESSION_PARTITION_ID, otherId.length() - 3));
		Map<Long, Set<Long>> equivalentExpressionMap = getEquivalentConceptMap(changeProcessor.getEquivalentConceptIds(), otherId -> otherId.startsWith(EXPRESSION_PARTITION_ID, otherId.length() - 3));

		for (Map.Entry<PostCoordinatedExpression, ComparableExpression> expressionEntry : classifiableFormsMap.entrySet()) {
			PostCoordinatedExpression expression = expressionEntry.getKey();
			ComparableExpression classifiableForm = expressionEntry.getValue();
			Long expressionId = classifiableForm.getExpressionId();
			ComparableExpression nnfExpression;
			if (!equivalenceTest) {
				// Create necessary normal form expression from classification results
				nnfExpression = createNNFExpression(expressionId, changeProcessor.getAddedStatements(), equivalentConceptMap);
			} else {
				// Lightweight nnf
				nnfExpression = new ComparableExpression().setExpressionId(expressionId);
			}
			nnfExpression.setDefinitionStatus(classifiableForm.getDefinitionStatus());
			nnfExpression.setEquivalentConcepts(equivalentConceptMap.get(expressionId));
			nnfExpression.setEquivalentExpressions(equivalentExpressionMap.get(expressionId));
			nnfExpression.setClassificationAncestors(changeProcessor.getTransitiveClosures().get(expressionId));
			expression.setNecessaryNormalForm(nnfExpression);
		}
	}

	private ComparableExpression createNNFExpression(Long tempExpressionId, Map<Long, Set<Relationship>> addedStatements, Map<Long, Set<Long>> equivalentConceptMap) {
		ComparableExpression nnfExpression = new ComparableExpression();

		final boolean equivalent = equivalentConceptMap.containsKey(tempExpressionId);
		if (equivalent) {
			for (Long equivalentId : equivalentConceptMap.get(tempExpressionId)) {
				nnfExpression.addFocusConcept(Long.toString(equivalentId));
			}
		}
		final Set<Relationship> nnfRelationships = addedStatements.get(tempExpressionId);
		// Put relationships into map to form groups
		Map<Integer, ComparableAttributeGroup> relationshipGroups = new HashMap<>();
		for (Relationship nnfRelationship : nnfRelationships) {
			if (nnfRelationship.getGroup() == 0) {
				if (nnfRelationship.getTypeId() == Concepts.IS_A_LONG) {
					if (!equivalent) {
						nnfExpression.addFocusConcept(Long.toString(nnfRelationship.getDestinationId()));
					}
				} else {
					nnfExpression.addAttribute(getComparableAttribute(nnfRelationship, addedStatements, equivalentConceptMap));
				}
			} else {
				relationshipGroups.computeIfAbsent(nnfRelationship.getGroup(), (i) -> new ComparableAttributeGroup())
						.addAttribute(getComparableAttribute(nnfRelationship, addedStatements, equivalentConceptMap));
			}
		}
		for (ComparableAttributeGroup group : relationshipGroups.values()) {
			nnfExpression.addAttributeGroup(group);
		}
		return nnfExpression;
	}

	private ComparableAttribute getComparableAttribute(Relationship nnfRelationship, Map<Long, Set<Relationship>> addedStatements, Map<Long, Set<Long>> equivalentConceptMap) {
		final long destinationId = nnfRelationship.getDestinationId();
		if (equivalentConceptMap.containsKey(destinationId)) {
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), Long.toString(equivalentConceptMap.get(destinationId).iterator().next()));
		} else if (addedStatements.containsKey(destinationId)) {
			// Destination is nested expression
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), new ComparableAttributeValue(createNNFExpression(destinationId, addedStatements, equivalentConceptMap)));
		} else {
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), Long.toString(destinationId));
		}
	}

	private Map<Long, Set<Long>> getEquivalentConceptMap(List<Set<Long>> equivalentConceptSets, Predicate<String> predicate) {
		Map<Long, Set<Long>> map = new HashMap<>();
		for (Set<Long> equivalentConceptSet : equivalentConceptSets) {
			for (Long concept : equivalentConceptSet) {
				for (Long otherConcept : equivalentConceptSet) {
					if (!concept.equals(otherConcept) && predicate.test(otherConcept.toString())) {
						map.computeIfAbsent(concept, (key) -> new HashSet<>()).add(otherConcept);
					}
				}
			}
		}
		return map;
	}

	private RelationshipChangeProcessor classifyTransientAxioms(Set<AxiomRepresentation> axioms, boolean equivalenceTest, String classificationPackage)
			throws IOException, ReasonerServiceException {

		final ClassificationContainer classificationContainer = setupContainer(classificationPackage);
		return snomedReasonerService.classifyTransientAxioms(axioms, equivalenceTest, classificationContainer);
	}

	private synchronized ClassificationContainer setupContainer(String classificationPackage) throws ReasonerServiceException {
		if (!classificationContainers.containsKey(classificationPackage)) {
			Set<String> toRemove = new HashSet<>();
			for (Map.Entry<String, ClassificationContainer> entry : classificationContainers.entrySet()) {
				entry.getValue().dispose();
				toRemove.add(entry.getKey());
			}
			toRemove.forEach(classificationContainers::remove);
			classificationContainers.put(classificationPackage, createClassificationContainer(classificationPackage));
		}
		return classificationContainers.get(classificationPackage);
	}

	private ClassificationContainer createClassificationContainer(String classificationPackage) throws ReasonerServiceException {
		try (InputStream file = releaseResourceManager.readResourceStreamOrNullIfNotExists(classificationPackage)) {
			if (file == null) {
				throw new IllegalStateException(format("For classification the SNOMED CT RF2 snapshot file for the dependant release is required. " +
								"For this request the RF2 archive '%s' is required. This file name includes the module id and effective time. " +
								"This file must be made available in the store configured using the '%s' configuration options.",
						classificationPackage, releaseResourceConfigName));
			}
		} catch (IOException e) {
			throw new ReasonerServiceException(format("IO exception while checking for package file %s", classificationPackage), e);
		}
		logger.info("Creating classification container for package {}", classificationPackage);

		try (InputStream previousReleaseStream = releaseResourceManager.readResourceStream(classificationPackage)) {
			return snomedReasonerService.classify(
					UUID.randomUUID().toString(),
					new InputStreamSet(previousReleaseStream),
					null,
					new FileOutputStream(Files.createTempFile("results" + UUID.randomUUID(), ".txt").toFile()), ELK_REASONER_FACTORY, false);
		} catch (IOException e) {
			throw new ReasonerServiceException(format("Failed to load package %s", classificationPackage), e);
		}
	}
}
