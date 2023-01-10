package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.MRCM;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExpressionContext {

	private final VersionControlHelper versionControlHelper;
	private final MRCMService mrcmService;
	private final String branch;
	private final TimerUtil timer;
	private final BranchService branchService;

	private BranchCriteria branchCriteria;
	private BranchCriteria dependantReleaseBranchCriteria;
	private MRCM mrcm;
	private Set<AttributeDomain> mrcmUngroupedAttributes;
	private Set<String> ancestorsAndSelf;
	private String focusConceptId;
	private Concept focusConcept;
	private ConceptService conceptService;

	public ExpressionContext(String branch, BranchService branchService, VersionControlHelper versionControlHelper, MRCMService mrcmService, TimerUtil timer) {
		this.branch = branch;
		this.branchService = branchService;
		this.versionControlHelper = versionControlHelper;
		this.mrcmService = mrcmService;
		this.timer = timer;
	}

	public BranchCriteria getBranchCriteria() {
		if (branchCriteria == null) {
			branchCriteria = versionControlHelper.getBranchCriteria(branch);
		}
		return branchCriteria;
	}

	public BranchCriteria getDependantReleaseBranchCriteria() throws ServiceException {
		if (dependantReleaseBranchCriteria == null) {
			if (PathUtil.isRoot(branch)) {
				throw new ServiceException("Expressions can not be maintained in the root branch. Please create a child codesystem and use the working branch of that codesystem.");
			}
			Branch latest = branchService.findLatest(branch);
			dependantReleaseBranchCriteria = versionControlHelper.getBranchCriteriaAtTimepoint(PathUtil.getParentPath(branch), latest.getBase());
		}
		return dependantReleaseBranchCriteria;
	}

	public MRCM getBranchMRCM() throws ServiceException {
		if (mrcm == null) {
			mrcm = mrcmService.loadActiveMRCMFromCache(branch);
		}
		return mrcm;
	}

	public Set<AttributeDomain> getMRCMUngroupedAttributes() throws ServiceException {
		if (mrcmUngroupedAttributes == null) {
			mrcmUngroupedAttributes = getBranchMRCM().getAttributeDomains().stream()
					.filter(Predicate.not(AttributeDomain::isGrouped))
					.collect(Collectors.toSet());
		}
		return mrcmUngroupedAttributes;
	}

	public Concept getFocusConceptWithActiveRelationships() {
		if (focusConcept == null) {
			Map<String, Concept> conceptMap = new HashMap<>();
			conceptMap.put(focusConceptId, new Concept(focusConceptId));
			conceptService.joinRelationships(conceptMap, new HashMap<>(), null, getBranch(), getBranchCriteria(), getTimer(), true);
			return conceptMap.get(focusConceptId);
		}
		return focusConcept;
	}

	public String getBranch() {
		return branch;
	}

	public TimerUtil getTimer() {
		return timer;
	}

	public void setAncestorsAndSelf(Set<Long> ancestorsAndSelf) {
		this.ancestorsAndSelf = ancestorsAndSelf.stream().map(Object::toString).collect(Collectors.toSet());
	}

	public Set<String> getAncestorsAndSelf() {
		return ancestorsAndSelf;
	}

	public void setFocusConceptId(String focusConceptId) {
		this.focusConceptId = focusConceptId;
	}

	public void setConceptService(ConceptService conceptService) {
		this.conceptService = conceptService;
	}

	public ConceptService getConceptService() {
		return conceptService;
	}
}
