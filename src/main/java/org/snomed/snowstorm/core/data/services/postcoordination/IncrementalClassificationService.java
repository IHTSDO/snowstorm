package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
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
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

import static java.lang.String.format;
import static org.snomed.otf.owltoolkit.service.SnomedReasonerService.ELK_REASONER_FACTORY;

@Service
public class IncrementalClassificationService {

	private final SnomedReasonerService snomedReasonerService;

	private final ResourceManager releaseResourceManager;

	private final Map<String, ClassificationContainer> classificationContainers = new HashMap<>();

	@Autowired
	private BranchService branchService;

	@Autowired
	private ExpressionAxiomConversionService expressionAxiomConversionService;

	@Autowired
	private CodeSystemService codeSystemService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public IncrementalClassificationService(@Autowired ResourceLoader cloudResourceLoader, @Autowired SnomedReleaseResourceConfiguration snomedReleaseResourceConfiguration) {
		this.releaseResourceManager = new ResourceManager(snomedReleaseResourceConfiguration, cloudResourceLoader);
		snomedReasonerService = new SnomedReasonerService();
	}

	public ComparableExpression classify(ComparableExpression classifiableForm, String branch) throws ServiceException {
		// Transform to axiom(s)
		Set<AxiomRepresentation> axioms = expressionAxiomConversionService.convertToAxioms(classifiableForm);

		// Classify
		final RelationshipChangeProcessor changeProcessor;
		try {
			changeProcessor = classify(axioms, "MAIN");
		} catch (IOException | ReasonerServiceException e) {
			throw new ServiceException("Failed to classify expression.", e);
		}
		final Map<Long, Set<Relationship>> addedStatements = changeProcessor.getAddedStatements();
		Map<Long, Long> equivalentConceptMap = getEquivalentConceptMap(changeProcessor.getEquivalentConceptIds());
		logger.info("Equivalent classes: {}", equivalentConceptMap);

		// Create necessary normal form expression from classification results
		return createNNFExpression(classifiableForm.getExpressionId(), addedStatements, equivalentConceptMap);
	}

	private ComparableExpression createNNFExpression(Long expressionId, Map<Long, Set<Relationship>> addedStatements, Map<Long, Long> equivalentConceptMap) {
		ComparableExpression nnfExpression = new ComparableExpression();
		nnfExpression.setExpressionId(expressionId);

		final boolean equivalent = equivalentConceptMap.containsKey(expressionId);
		if (equivalent) {
			nnfExpression.addFocusConcept(Long.toString(equivalentConceptMap.get(expressionId)));
		}
		final Set<Relationship> nnfRelationships = addedStatements.get(nnfExpression.getExpressionId());
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

	private ComparableAttribute getComparableAttribute(Relationship nnfRelationship, Map<Long, Set<Relationship>> addedStatements, Map<Long, Long> equivalentConceptMap) {
		final long destinationId = nnfRelationship.getDestinationId();
		if (equivalentConceptMap.containsKey(destinationId)) {
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), Long.toString(equivalentConceptMap.get(destinationId)));
		} else if (addedStatements.containsKey(destinationId)) {
			// Destination is nested expression
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), new ComparableAttributeValue(createNNFExpression(destinationId, addedStatements, equivalentConceptMap)));
		} else {
			return new ComparableAttribute(Long.toString(nnfRelationship.getTypeId()), Long.toString(destinationId));
		}
	}

	private Map<Long, Long> getEquivalentConceptMap(List<Set<Long>> equivalentConceptSets) {
		Map<Long, Long> map = new HashMap<>();
		for (Set<Long> equivalentConceptSet : equivalentConceptSets) {
			for (Long concept : equivalentConceptSet) {
				for (Long otherConcept : equivalentConceptSet) {
					final String otherConceptId = otherConcept.toString();
					if (!concept.equals(otherConcept) && !otherConceptId.startsWith("06", otherConceptId.length() - 3)) {
						map.put(concept, otherConcept);
					}
				}
			}
		}
		return map;
	}

	private RelationshipChangeProcessor classify(Set<AxiomRepresentation> axioms, String branch) throws IOException, ReasonerServiceException {
		final ClassificationContainer classificationContainer = setupContainer(branch);
		return snomedReasonerService.classifyAxioms(axioms, classificationContainer);
	}

	private synchronized ClassificationContainer setupContainer(String branch) throws ReasonerServiceException {
		if (!classificationContainers.containsKey(branch)) {
			Set<String> toRemove = new HashSet<>();
			for (Map.Entry<String, ClassificationContainer> entry : classificationContainers.entrySet()) {
				entry.getValue().dispose();
				toRemove.add(entry.getKey());
			}
			toRemove.forEach(classificationContainers::remove);
			classificationContainers.put(branch, createClassificationContainer(branch));
		}
		return classificationContainers.get(branch);
	}

	private ClassificationContainer createClassificationContainer(String branch) throws ReasonerServiceException {
		// TODO: Lookup package from dependant codesystem
		Branch branchWithInheritedMetadata = branchService.findBranchOrThrow(branch, false);
		Metadata metadata = branchWithInheritedMetadata.getMetadata();
		String previousPackage = metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE);
		if (previousPackage == null) {
			throw new IllegalStateException(format("No %s set in branch metadata.", BranchMetadataKeys.PREVIOUS_PACKAGE));
		}
		logger.info("Creating classification container for branch {} using package {}", branch, previousPackage);

		try (InputStream previousReleaseStream = releaseResourceManager.readResourceStream(previousPackage)) {
			return snomedReasonerService.classify(
					UUID.randomUUID().toString(),
					new InputStreamSet(previousReleaseStream),
					null,
					new FileOutputStream(Files.createTempFile("results" + UUID.randomUUID(), ".txt").toFile()), ELK_REASONER_FACTORY, false);
		} catch (IOException e) {
			throw new ReasonerServiceException(format("Failed to load previous package %s", previousPackage), e);
		}
	}
}
