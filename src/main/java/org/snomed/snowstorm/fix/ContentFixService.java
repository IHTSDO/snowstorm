package org.snomed.snowstorm.fix;

import org.snomed.snowstorm.fix.service.DupeLangRefsetFixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ContentFixService {

	@Autowired
	private DupeLangRefsetFixService dupeLangRefsetFixService;

	public void runContentFix(String path, ContentFixType contentFixType, Set<Long> conceptIds) {
		if (contentFixType == ContentFixType.DUPLICATE_LANGUAGE_REFERENCE_SET_ENTRIES) {
			dupeLangRefsetFixService.fixConcepts(path, conceptIds);
		}
	}

}
