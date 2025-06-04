package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static org.hl7.fhir.r4.model.Bundle.BundleType.BATCH;
import static org.hl7.fhir.r4.model.Bundle.BundleType.BATCHRESPONSE;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Component
public class FHIRBundleProvider implements IResourceProvider {

	private static final Logger log = LoggerFactory.getLogger(FHIRBundleProvider.class);

	@Autowired
	private FhirContext fhirContext;

	@Transaction
	public Bundle handleBatch(@TransactionParam Bundle bundle) {
		if(BATCH.equals(bundle.getType())) {
			log.info("Batch processing started");
			final String baseFhirUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + "/fhir/";
			IGenericClient client = fhirContext.newRestfulGenericClient(baseFhirUrl);
			Bundle response = new Bundle();
			response.setType(BATCHRESPONSE);

			for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
				try {
					response.addEntry(handleBundleEntry(bundleEntryComponent, client));
				} catch (Exception e) {
					log.error(e.getMessage(), e);
					response.addEntry(buildErrorEntry(e.getMessage()));
				}
            }

			log.info("Batch processing finished");
			return response;
		}
		throw exception("Failed to handle bundle resource, bundle type '" + bundle.getType() + "' not supported", IssueType.EXCEPTION, 400);
	}

	private Bundle.BundleEntryComponent handleBundleEntry(Bundle.BundleEntryComponent entry, IGenericClient client) {
		String[] url = entry.getRequest().getUrl().split("/");
		String type = url[0];
		String operation = url[1];

		Parameters result = client
				.operation()
				.onType(type)
				.named(operation)
				.withParameters((Parameters) entry.getResource())
				.execute();

		Bundle.BundleEntryComponent responseEntry = new Bundle.BundleEntryComponent();
		responseEntry.setResource(result);
		Bundle.BundleEntryResponseComponent responseComponent = new Bundle.BundleEntryResponseComponent();
		responseComponent.setStatus("200 OK");
		responseEntry.setResponse(responseComponent);
		return responseEntry;
	}

	private Bundle.BundleEntryComponent buildErrorEntry(String error) {
		Bundle.BundleEntryComponent responseEntry = new Bundle.BundleEntryComponent();
		Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent();
		response.setStatus("500 Internal Server Error");
		OperationOutcome operationOutcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		issue.setDiagnostics(error);
		operationOutcome.addIssue(issue);
		response.setOutcome(operationOutcome);
		responseEntry.setResponse(response);
		return responseEntry;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Bundle.class;
	}
}
