package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.repositories.FHIRCodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FHIRCodeSystemService {

	@Autowired
	private FHIRCodeSystemRepository codeSystemRepository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRCodeSystemVersion save(CodeSystem codeSystem) {
		FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion(codeSystem);
		wrap(fhirCodeSystemVersion);
		logger.info("Saving fhir code system '{}'.", fhirCodeSystemVersion.getId());
		codeSystemRepository.save(fhirCodeSystemVersion);
		return fhirCodeSystemVersion;
	}

	public FHIRCodeSystemVersion findCodeSystemVersion(StringType systemUrl) {
		// TODO: use version in param
		FHIRCodeSystemVersion version = codeSystemRepository.findFirstByUrlOrderByVersionDesc(systemUrl.getValue());
		unwrap(version);
		return version;
	}

	private void wrap(FHIRCodeSystemVersion fhirCodeSystemVersion) {
		if (fhirCodeSystemVersion.getVersion() == null) {
			fhirCodeSystemVersion.setVersion("");
		}
	}

	private void unwrap(FHIRCodeSystemVersion version) {
		if (version != null && "".equals(version.getVersion())) {
			version.setVersion(null);
		}
	}

	public Iterable<FHIRCodeSystemVersion> findAll() {
		return codeSystemRepository.findAll();
	}
}
