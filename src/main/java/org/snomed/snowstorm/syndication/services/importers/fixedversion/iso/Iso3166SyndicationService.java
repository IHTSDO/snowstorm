package org.snomed.snowstorm.syndication.services.importers.fixedversion.iso;

import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.springframework.stereotype.Service;

import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ISO3166_CODESYSTEM;

@Service(ISO3166_CODESYSTEM)
public class Iso3166SyndicationService extends FixedVersionSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return ISO3166_CODESYSTEM;
    }
}
