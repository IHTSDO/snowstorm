package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.exceptions.ElasticsearchExceptionInterceptor;
import org.snomed.snowstorm.fhir.services.*;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class HapiRestfulServlet extends RestfulServer {

    private static final long serialVersionUID = 1L;

    private final transient BuildProperties buildProperties;

	private final transient FHIRCodeSystemService codeSystemService;

    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    public HapiRestfulServlet(BuildProperties buildProperties, FHIRCodeSystemService codeSystemService) {
        this.buildProperties = buildProperties;
        this.codeSystemService = codeSystemService;
    }

    /**
     * The initialize method is automatically called when the servlet is starting up, so it can be used to configure the
     * servlet to define resource providers, or set up configuration, interceptors, etc.
     */
    @Override
    protected void initialize() {
        final WebApplicationContext applicationContext =
                WebApplicationContextUtils.getWebApplicationContext(this.getServletContext());

		if (applicationContext == null) {
			throw new IllegalStateException("Failed to recover web application context while initializing HAPI FHIR servlet");
		}

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
				applicationContext.getBean(FHIRBundleProvider.class),
				applicationContext.getBean(FHIRStructureDefinitionProvider.class)
		);

		registerProvider(applicationContext.getBean(FHIRVersionsOperationProvider.class));

        setServerConformanceProvider(new FHIRTerminologyCapabilitiesProvider(this, buildProperties, codeSystemService));

        // Register interceptors
        registerInterceptor(new RootInterceptor());

	    registerInterceptor(new ElasticsearchExceptionInterceptor());

        logger.info("FHIR Resource providers and interceptors registered");
    }

}
