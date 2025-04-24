package org.snomed.snowstorm.syndication.codesystem.bcp;

import org.snomed.snowstorm.syndication.codesystem.CodeSystemSyndicationService;
import org.springframework.stereotype.Service;

import static org.snomed.snowstorm.syndication.common.SyndicationConstants.BCP47_CODESYSTEM;

@Service(BCP47_CODESYSTEM)
public class Bcp47SyndicationService extends CodeSystemSyndicationService {

    @Override
    protected String getCodeSystemName() {
        return BCP47_CODESYSTEM;
    }
}
