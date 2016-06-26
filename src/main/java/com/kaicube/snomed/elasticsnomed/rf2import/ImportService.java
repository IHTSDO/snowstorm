package com.kaicube.snomed.elasticsnomed.rf2import;

import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ImportService {

	@Autowired
	private ConceptService conceptService;

	public void importSnapshot(String releaseDirPath, String branchPath) throws ReleaseImportException {
		ComponentFactoryImpl componentFactory = new ComponentFactoryImpl();
		ReleaseImporter releaseImporter = new ReleaseImporter(componentFactory);
		releaseImporter.loadReleaseFiles(releaseDirPath, LoadingProfile.full);

		final List<Concept> concepts = componentFactory.getConcepts();
		conceptService.bulkImport(concepts, branchPath);
	}
}
