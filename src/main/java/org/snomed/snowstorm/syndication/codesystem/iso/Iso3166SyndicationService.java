package org.snomed.snowstorm.syndication.codesystem.iso;

import org.snomed.snowstorm.syndication.codesystem.CodeSystemSyndicationService;
import org.springframework.stereotype.Service;

import static org.snomed.snowstorm.syndication.common.SyndicationConstants.ISO3166_CODESYSTEM;

@Service(ISO3166_CODESYSTEM)
public class Iso3166SyndicationService extends CodeSystemSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return ISO3166_CODESYSTEM;
    }
}
