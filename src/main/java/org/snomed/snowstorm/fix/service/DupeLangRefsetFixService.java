package org.snomed.snowstorm.fix.service;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DupeLangRefsetFixService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void fixConcepts(String path, Set<Long> conceptIds) {
		try (final Commit commit = branchService.openCommit(path)) {

			final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
			final Collection<Concept> concepts = conceptService.find(branchCriteria, path, conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS);
			List<ReferenceSetMember> membersToSave = new ArrayList<>();
			for (Concept concept : concepts) {
				for (Description description : concept.getDescriptions()) {
					for (Map.Entry<String, Set<ReferenceSetMember>> langRefsetEntry : description.getLangRefsetMembersMap().entrySet()) {
						if (langRefsetEntry.getValue().size() > 1) {
							List<ReferenceSetMember> langRefsets = new ArrayList<>(langRefsetEntry.getValue());
							langRefsets.sort(Comparator.comparing(ReferenceSetMember::isReleased).thenComparing(ReferenceSetMember::isActive).reversed());
							for (int i = 0; i < langRefsets.size(); i++) {
								boolean active = i == 0;
								final ReferenceSetMember member = langRefsets.get(i);
								if (member.isActive() != active) {
									member.setActive(active);
									member.markChanged();
									membersToSave.add(member);
								}
							}
						}
					}

				}
			}

			if (!membersToSave.isEmpty()) {
				logger.info("Saving {} corrected language refset members.", membersToSave.size());
				referenceSetMemberService.doSaveBatchMembers(membersToSave, commit);
				commit.markSuccessful();
			} else {
				logger.info("No language refset members found to correct.");
			}
		}
	}

}
