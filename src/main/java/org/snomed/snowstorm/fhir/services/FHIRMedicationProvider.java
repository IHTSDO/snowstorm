package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.pojo.*;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

@Component
public class FHIRMedicationProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private FHIRHelper fhirHelper;
	
	//TOODO Implement Find Resource taking filters for strength, ingredient and dose form
	
	@Read()
	public Medication getMedication(HttpServletRequest request,
									@IdParam IdType id) throws FHIROperationException {
		Medication medication = new Medication();
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request.getHeader(ACCEPT_LANGUAGE_HEADER));
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(id.getIdPart(), languageDialects, "MAIN"));
		medication.setId(id);
		TermLangPojo fsn = concept.getFsn(languageDialects);
		CodeableConcept code = new CodeableConcept(new Coding(SNOMED_URI, concept.getId(), fsn.getTerm()));
		medication.setCode(code);
		Set<Integer> groups = new HashSet<>();
		List<Relationship> rels = concept.getRelationships(true, null, null, Concepts.INFERRED_RELATIONSHIP);
		for (Relationship r : rels) {
			//Pull out active ingredient / precise active ingredient
			if (r.getTypeId().equals("127489000") || r.getTypeId().equals("762949000")) {
				groups.add(r.getGroupId());
				Substance substance = new Substance();
				substance.setId("#" + r.getDestinationId());
				CodeableConcept substanceCode = new CodeableConcept();
				Coding coding = new Coding(SNOMED_URI, r.getDestinationId(), r.getTargetFsn());
				substanceCode.addCoding(coding);
				substance.setCode(substanceCode);
				medication.addContained(substance);
			}
			
			//Pull out Dose Form
			if (r.getTypeId().equals("411116001")) {
				CodeableConcept formCode = new CodeableConcept();
				Coding coding = new Coding(SNOMED_URI, r.getDestinationId(), r.getTargetFsn());
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
			throw new FHIROperationException("the concept does not have properties mapped to FHIR Medication Resource.", IssueType.CODEINVALID, 400);
		} else {
			// Generate Narrative TODO: implement automatic generation with context
			Narrative narrative = new Narrative();
			narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
			String div = "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p><b>Generated Narrative with Details</b></p>";
			div += "<p>id:" + concept.getId() + "</p>";
			div += "<p>code:" + fsn.getTerm() + "</p>";
			div += "</div>";
			narrative.setDivAsString(div);
			medication.setText(narrative);
			return medication;
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Medication.class;
	}
}
