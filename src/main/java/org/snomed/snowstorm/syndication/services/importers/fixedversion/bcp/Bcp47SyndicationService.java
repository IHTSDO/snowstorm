package org.snomed.snowstorm.syndication.services.importers.fixedversion.bcp;

import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.springframework.stereotype.Service;

import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.BCP47_CODESYSTEM;

@Service(BCP47_CODESYSTEM)
public class Bcp47SyndicationService extends FixedVersionSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return BCP47_CODESYSTEM;
    }
}
