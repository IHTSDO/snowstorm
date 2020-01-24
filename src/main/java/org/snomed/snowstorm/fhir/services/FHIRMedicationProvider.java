package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FHIRMedicationProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private HapiParametersMapper mapper;
	
	@Autowired
	private FHIRHelper fhirHelper;

	@Read()
	// XXX There are a lot of hardcoded values in here including language and branch. This should be cleanup up before it goes to master.
	public Medication getMedication(@IdParam IdType id) throws FHIROperationException {
		Medication medication = new Medication();
		List<String> languageCodes = new ArrayList<>();
		languageCodes.add("es");
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(languageCodes);
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(id.getIdPart(), languageDialects, "MAIN"));
		medication.setId(id);
		String fsn = getFSN(concept, "es");
		CodeableConcept code = new CodeableConcept(new Coding(FHIRConstants.SNOMED_URI, concept.getId(), fsn));
		medication.setCode(code);
		Set<Integer> groups = new HashSet<>();
		List<Relationship> rels = concept.getRelationships(true, null, null, Concepts.INFERRED_RELATIONSHIP);
		for (Relationship r : rels) {
			if (r.getTypeId().equals("762949000")) {
				groups.add(r.getGroupId());
				Substance substance = new Substance();
				substance.setId("#" + r.getDestinationId());
				CodeableConcept substanceCode = new CodeableConcept();
				Coding coding = new Coding(FHIRConstants.SNOMED_URI, r.getDestinationId(), r.getTargetFsn());
				substanceCode.addCoding(coding);
				substance.setCode(substanceCode);
				medication.addContained(substance);
			}
			if (r.getTypeId().equals("411116001")) {
				CodeableConcept formCode = new CodeableConcept();
				Coding coding = new Coding(FHIRConstants.SNOMED_URI, r.getDestinationId(), r.getTargetFsn());
				formCode.addCoding(coding);
				medication.setForm(formCode);
			}
		}
		for (Integer groupId : groups) {
			Medication.MedicationIngredientComponent i = new Medication.MedicationIngredientComponent();
			Ratio ratio = new Ratio();
			Quantity numerator = new Quantity();
			Quantity denominator = new Quantity();
			for (Relationship r : rels) {
				if (r.getGroupId() == groupId) {
					if (r.getTypeId().equals("762949000")) {
						Reference ref = new Reference("#" + r.getDestinationId());
						i.setItem(ref);
					}
					if (r.getTypeId().equals("732944001")) {
						numerator.setValue(Long.parseLong(r.getTargetFsn().replaceAll("\\D+","")));
					}
					if (r.getTypeId().equals("732945000")) {
						numerator.setUnit(r.getTargetFsn().replaceAll("\\(.*\\)", "").trim());
					}
					if (r.getTypeId().equals("732946004")) {
						denominator.setValue(Long.parseLong(r.getTargetFsn().replaceAll("\\D+","")));
					}
					if (r.getTypeId().equals("732945000")) {
						denominator.setUnit(r.getTargetFsn().replaceAll("\\(.*\\)", "").trim());
					}
				}
			}
			ratio.setNumerator(numerator);
			ratio.setDenominator(denominator);
			i.setStrength(ratio);
			medication.addIngredient(i);
		}
		if (medication.getIngredient().size() == 0) {
			throw new FHIROperationException(IssueType.CODEINVALID, "the concept does not have properties mapped to FHIR Medication Resource.");
		} else {
			// Generate Narrative TODO: implement automatic generation with context
			Narrative narrative = new Narrative();
			narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
			String div = "<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\"><p><b>Generated Narrative with Details</b></p>";
			div += "<p>id:" + concept.getId() + "</p>";
			div += "<p>code:" + fsn + "</p>";
			div += "</div>";
			narrative.setDivAsString(div);
			medication.setText(narrative);
			return medication;
		}
	}

	private String getFSN(Concept c, String languageCode) {
		String term = "";
		for (Description d : c.getActiveDescriptions()) {
			if (d.getTypeId().equals(Concepts.FSN) && d.getLanguageCode().equals(languageCode)) {
				term = d.getTerm();
			}
		}
		return term;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Medication.class;
	}
}
