package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.services.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletException;

public class HapiRestfulServlet extends RestfulServer {

	private static final long serialVersionUID = 1L;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The initialize method is automatically called when the servlet is starting up, so it can be used to configure the
	 * servlet to define resource providers, or set up configuration, interceptors, etc.
	 */
	@Override
	protected void initialize() throws ServletException {
		final WebApplicationContext applicationContext =
				WebApplicationContextUtils.getWebApplicationContext(this.getServletContext());

		setDefaultResponseEncoding(EncodingEnum.JSON);

		final FhirContext fhirContext = applicationContext.getBean(FhirContext.class);
		final LenientErrorHandler delegateHandler = new LenientErrorHandler();
		fhirContext.setParserErrorHandler(new StrictErrorHandler() {
			@Override
			public void unknownAttribute(IParseLocation theLocation, String theAttributeName) {
				delegateHandler.unknownAttribute(theLocation, theAttributeName);
			}

			@Override
			public void unknownElement(IParseLocation theLocation, String theElementName) {
				delegateHandler.unknownElement(theLocation, theElementName);
			}

			@Override
			public void unknownReference(IParseLocation theLocation, String theReference) {
				delegateHandler.unknownReference(theLocation, theReference);
			}
		});
		setFhirContext(fhirContext);

		FHIRHelper fhirHelper = applicationContext.getBean(FHIRHelper.class);
		fhirHelper.setFhirContext(fhirContext);

		/*
		 * The servlet defines any number of resource providers, and configures itself to use them by calling
		 * setResourceProviders()
		 */
		setResourceProviders(
				applicationContext.getBean(FHIRCodeSystemProvider.class),
				applicationContext.getBean(FHIRValueSetProvider.class),
				applicationContext.getBean(FHIRConceptMapProvider.class),
				applicationContext.getBean(FHIRMedicationProvider.class),
				applicationContext.getBean(FHIRStructureDefinitionProvider.class));

		setServerConformanceProvider(new FHIRTerminologyCapabilitiesProvider(this));

		// Register interceptors
		registerInterceptor(new RootInterceptor());

		logger.info("FHIR Resource providers and interceptors registered");
	}
}
