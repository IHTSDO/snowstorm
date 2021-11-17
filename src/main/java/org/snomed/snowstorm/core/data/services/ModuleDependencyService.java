package org.snomed.snowstorm.core.data.services;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;

/*
 * Service to create module dependency refset members as required, either temporarily
 * for export, or persisted eg during versioning.
 * 
 * Note we have a problem here that our dependency date can only be calculated if it's known to the 
 * code system, so for extensions on extensions, we'll need to find the parent branch and then
 * get THAT dependency date.
 */
@Service
public class ModuleDependencyService extends ComponentService {
	
	public static final int RECURSION_LIMIT = 100;
	public static final String SOURCE_ET = "sourceEffectiveTime";
	public static final String TARGET_ET = "targetEffectiveTime";
	public static final PageRequest LARGE_PAGE = PageRequest.of(0,10000);
	
	public static final Set<String> CORE_MODULES = Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE);
	
	public Set<String> SI_MODULES = new HashSet<>(Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE, Concepts.ICD10_MODULE));
	
	@Autowired
	private BranchService branchService;
	
	@Autowired
	private AuthoringStatsService statsService;
	
	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private ReferenceSetMemberRepository memberRepository;
	
	@Autowired
	@Lazy
	private ReferenceSetMemberService refsetService;
	
	@Value("${mdrs.exclude.derivative-modules}")
	private boolean excludeDerivativeModules;
	
	@Value("#{'${mdrs.blocklist}'.split('\\s*,\\s*')}")  //Split and trim spaces
	private List<String> blockListedModules;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private long cacheValidAt = 0L;
	private Set<String> cachedInternationalModules;
	
	//Derivative modules are those belonging with the International Edition (like the GPS)
	//but not packaged with it, so not included eg in the US Edition
	private Set<String> derivativeModules;
	
	//Refresh when the HEAD time is greater than the HEAD time on MAIN
	//Check every 30 mins to save time when export operation actually called
	@Scheduled(fixedDelay = 1800_000, initialDelay = 180_000)
	public synchronized void refreshCache() {
		//Do we need to refresh the cache?
		long currentTime = branchService.findBranchOrThrow(Branch.MAIN).getHeadTimestamp();
		if (currentTime > cacheValidAt) {
			//Map of components to Maps of ModuleId -> Counts, pull out all the 2nd level keys into a set
			Map<String, Map<String, Long>> moduleCountMap = statsService.getComponentCountsPerModule(Branch.MAIN);
			cachedInternationalModules= moduleCountMap.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
			cacheValidAt = currentTime;
			logger.info("MDR cache of International Modules refreshed for HEAD time: {}", currentTime);
			
			//During unit tests, or in non-standard installations we might not see the ICD-10 Module
			if (!cachedInternationalModules.contains(Concepts.ICD10_MODULE)) {
				SI_MODULES.remove(Concepts.ICD10_MODULE);
			}
			
			derivativeModules = cachedInternationalModules.stream()
					.filter(m -> !SI_MODULES.contains(m))
					.collect(Collectors.toSet());
			
			logger.info("{} derivative modules set to be {} in MDRS export.", derivativeModules.size(), (excludeDerivativeModules?"excluded":"included"));
		}
	}
	
	/**
	 * @param branchPath
	 * @param effectiveDate
	 * @param modulesIncluded
	 * @param commit optionally passed to persist generated members
	 * @return
	 */
	public Set<ReferenceSetMember> generateModuleDependencies(String branchPath, String effectiveDate, Set<String> modulesIncluded, Commit commit) {
		StopWatch sw = new StopWatch("MDRGeneration");
		sw.start();
		refreshCache();
		
		//What module dependency refset members already exist?
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet(Concepts.REFSET_MODULE_DEPENDENCY);
		Page<ReferenceSetMember> rmPage = refsetService.findMembers(branchPath, searchRequest, LARGE_PAGE);
		Map<String, Set<ReferenceSetMember>> moduleMap = rmPage.getContent().stream()
				.collect(Collectors.groupingBy(ReferenceSetMember::getModuleId, Collectors.toSet()));
		
		//What modules are known to this code system?
		CodeSystem cs = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		Branch branch = branchService.findBranchOrThrow(branchPath);
		
		//Do I have a dependency release?  If so, what's its effective time?
		boolean isEdition = true;
		boolean isInternational = true;
		Integer dependencyET = null;
		if (cs == null) {
			logger.warn("No CodeSystem associated with branch {} assuming International CodeSystem", branchPath);
		} else {
			dependencyET = cs.getDependantVersionEffectiveTime();
		}
		
		if (dependencyET == null) {
			dependencyET = Integer.parseInt(effectiveDate);
		} else {
			isInternational = false;
		}
		
		if (branch.getMetadata() != null && branch.getMetadata().getString(BranchMetadataKeys.DEPENDENCY_PACKAGE) != null ) {
			isEdition = false;
		}
		
		//What modules are actually present in the content?
		Map<String,Map<String,Long>> moduleCountMap = statsService.getComponentCountsPerModule(branchPath);
		Set<String> modulesWithContent = moduleCountMap.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
		
		Set<String> modulesRequired = new HashSet<>();
		//US Edition (for example) will include the ICD-10 Module
		if (isEdition) {
			modulesRequired.addAll(SI_MODULES);
		}
		
		if (modulesIncluded != null && !modulesIncluded.isEmpty()) {
			modulesRequired.addAll(modulesIncluded);
			//But we only want to retain those modules that actually have content
			//Especially because the SRS requests all known modules regardless
			modulesRequired.retainAll(modulesWithContent);
		} else {
			modulesRequired = modulesWithContent;
		}
		
		//If we're not an Edition, remove all international modules
		if (!isEdition) {
			modulesRequired.removeAll(cachedInternationalModules);
		}
		
		//Recover all these module concepts to find out what module they themselves were defined in.
		//Generally, refset type modules are defined in the main extension module
		Map<String, String> moduleHierarchyMap = getModuleOfModule(branchPath, new HashSet<>(modulesRequired));
		
		//For extensions, the module with the concepts in it is assumed to be the parent, UNLESS
		//a module concept is defined in another module, in which case the owning module is the parent. 
		//Calculate the top level for any given extension, so we know when to stop!
		String topLevelExtensionModule = null;
		if (!isInternational) {
			Long greatestCount = 0L;
			for (Map.Entry<String, Long> moduleCount : moduleCountMap.get("Concept").entrySet()) {
				if (!cachedInternationalModules.contains(moduleCount.getKey()) && 
						(topLevelExtensionModule == null || greatestCount < moduleCount.getValue())) {
					topLevelExtensionModule = moduleCount.getKey();
					greatestCount = moduleCount.getValue();
				}
			}
			if (moduleHierarchyMap.get(topLevelExtensionModule) == null || 
					moduleHierarchyMap.get(topLevelExtensionModule).contentEquals(topLevelExtensionModule)) {
				moduleHierarchyMap.put(topLevelExtensionModule, Concepts.CORE_MODULE);
			}
		}

		//Remove any map entries that we don't need
		moduleMap.keySet().retainAll(modulesRequired);
		
		logger.info("Generating MDR for {}, {} modules [{}]", branchPath, isInternational ? "international" : "extension", String.join(", ", modulesRequired));
		
		//Update or augment module dependencies as required
		int recursionLimit = 0;
		for (String moduleId : modulesRequired) {
			boolean isExtensionMod = !cachedInternationalModules.contains(moduleId);

			Set<ReferenceSetMember> moduleDependencies = moduleMap.computeIfAbsent(moduleId, k -> new HashSet<>());

			if (isInternational && isExtensionMod) {
				logger.warn("CHECK LOGIC: {} thought to be both International and an Extension Module", moduleId);
			}
			
			String thisLevel = moduleId;
			while (!thisLevel.equals(Concepts.MODEL_MODULE)){
				thisLevel = updateOrCreateModuleDependency(moduleId, 
						thisLevel, 
						moduleHierarchyMap, 
						moduleDependencies,
						effectiveDate,
						dependencyET,
						branchPath,
						isInternational);
				if (++recursionLimit > RECURSION_LIMIT) {
					throw new IllegalStateException ("Recursion limit reached calculating MDR in " + branchPath + " for module " + thisLevel);
				}
			}
		}

		sw.stop();
		logger.info("MDR generation for {}, modules [{}] took {}s", branchPath, String.join(", ", modulesRequired), sw.getTotalTimeSeconds());
		Set<ReferenceSetMember> mdrMembers = moduleMap.values().stream()
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
		
		if (commit != null) {
			List<ReferenceSetMember> updatedMDRmembers = mdrMembers.stream()
					.filter(rm -> rm.getEffectiveTime().equals(effectiveDate))
					.collect(Collectors.toList());
			doSaveBatchComponents(updatedMDRmembers, commit, ReferenceSetMember.Fields.MEMBER_ID, memberRepository);
		}
		return mdrMembers;
	}

	private String updateOrCreateModuleDependency(String moduleId, String thisLevel, Map<String, String> moduleParents,
			Set<ReferenceSetMember> moduleDependencies, String effectiveDate, Integer dependencyET, String branchPath, boolean isInternational) {
		//Take us up a level
		String nextLevel = moduleParents.get(thisLevel);
		if (nextLevel == null) {
			throw new IllegalStateException("Unable to calculate module dependency via parent module of " + thisLevel + " in " + branchPath + " due to no parent mapped for module " + thisLevel);
		}
		ReferenceSetMember rm = findOrCreateModuleDependency(moduleId, moduleDependencies, nextLevel);
		moduleDependencies.add(rm);
		rm.setRefsetId(Concepts.REFSET_MODULE_DEPENDENCY);
		if (!isInternational && cachedInternationalModules.contains(moduleId)) {
			rm.setAdditionalField(SOURCE_ET, dependencyET.toString());
		} else {
			rm.setEffectiveTimeI(Integer.parseInt(effectiveDate));
			rm.setAdditionalField(SOURCE_ET, effectiveDate);
			rm.markChanged();
		}
		
		if (rm.getEffectiveTime() == null) {
			rm.setEffectiveTimeI(Integer.parseInt(effectiveDate));
		}
		
		//Now is this module part of our dependency, or is it unique part of this CodeSystem?
		//For now, we'll assume the International Edition is the dependency
		if (cachedInternationalModules.contains(nextLevel)) {
			rm.setAdditionalField(TARGET_ET, dependencyET.toString());
		} else {
			rm.setAdditionalField(TARGET_ET, effectiveDate);
		}
		return nextLevel;
	}

	//TODO Look out for inactive reference set members and use active by preference
	//or reactive inactive if required.
	private ReferenceSetMember findOrCreateModuleDependency(String moduleId, Set<ReferenceSetMember> moduleDependencies,
			String targetModuleId) {
		for (ReferenceSetMember thisMember : moduleDependencies) {
			if (thisMember.getReferencedComponentId().equals(targetModuleId)) {
				return thisMember;
			}
		}
		//Create if not found
		ReferenceSetMember rm = new ReferenceSetMember();
		rm.setMemberId(UUID.randomUUID().toString());
		rm.setModuleId(moduleId);
		rm.setReferencedComponentId(targetModuleId);
		rm.setActive(true);
		rm.setCreating(true);
		return rm;
	}

	private Map<String, String> getModuleOfModule(String branchPath, Set<String> moduleIds) {
		//Looking up the modules that concepts are declared in doesn't give guaranteed right answer
		//eg INT derivative packages are declared in model module but obviously reference core concepts
		//Hard code international modules other than model to core
		Map<String, String> moduleParentMap =  moduleIds.stream()
				.filter(m -> cachedInternationalModules.contains(m))
				.collect(Collectors.toMap(m -> m, m -> Concepts.CORE_MODULE));
		
		//Also always ensure that our two international modules are populated
		moduleParentMap.put(Concepts.CORE_MODULE, Concepts.MODEL_MODULE);
		moduleParentMap.remove(Concepts.MODEL_MODULE);
		
		//So we don't need to look these up
		Set<Long> conceptIds = moduleIds.stream()
				.filter(m -> !cachedInternationalModules.contains(m))
				.map(Long::parseLong)
				.collect(Collectors.toSet());
		
		
		int recursionDepth = 0;
		//Repeat lookup of parents until all modules encountered are populated in the map
		while (!conceptIds.isEmpty()) {
			Page<Concept> modulePage = conceptService.find(new ArrayList<>(conceptIds), null, branchPath, LARGE_PAGE);
			Map<String, String> partialParentMap = modulePage.getContent().stream()
					.collect(Collectors.toMap(Concept::getId, SnomedComponent::getModuleId));
				moduleParentMap.putAll(partialParentMap);
			int foundCount = modulePage.getContent().size();
			if (foundCount != conceptIds.size()) {
				String msg = "Found " + foundCount + " but expected " + conceptIds.size() + " module concepts in " + branchPath;
				logger.error(msg);
				
				//What are we missing?
				Set<String> allMissing = new HashSet<>(moduleIds);
				allMissing.removeAll(moduleParentMap.keySet());
				
				//Debug. If we end up with nothing missing here, then what did we have already that we didn't recover?
				if (allMissing.isEmpty()) {
					Set<String> found = modulePage.getContent().stream().map(c -> c.getId()).collect(Collectors.toSet());
					logger.warn("MDRS Requested: " + StringUtils.join(conceptIds, ','));
					logger.warn("MDRS Received: " + StringUtils.join(found, ','));
				} else {
					//Did we find even 1 module concept?
					String bestFind = moduleParentMap.keySet().iterator().hasNext()?moduleParentMap.keySet().iterator().next():null;
					if (bestFind == null) {
						bestFind = Concepts.CORE_MODULE;
					}
					logger.info("Populating 'best effort' for missing modules: [{}] -> {}", String.join(", ", allMissing), bestFind);
					
					for (String missing : allMissing) {
						String parent = missing.equals(Concepts.CORE_MODULE)?Concepts.MODEL_MODULE:bestFind;
						moduleParentMap.put(missing, parent);
					}
					return moduleParentMap;
				}
			}
			//Now look up all these new parents as well, unless we've already got them in our map
			//And if the module was defined in the model module, then there's no need to look that up
			conceptIds = partialParentMap.values().stream()
					.filter(m -> !moduleParentMap.containsKey(m))
					.filter(m -> !m.equals(Concepts.MODEL_MODULE))
					.map(Long::parseLong)
					.collect(Collectors.toSet());
			
			if (++recursionDepth > RECURSION_LIMIT) {
				throw new IllegalStateException("Recursion depth reached looking for module parents in " + branchPath + " with " + String.join(",", moduleIds));
			}
		}
		return moduleParentMap;
	}
	
	public boolean isExportable(ReferenceSetMember rm, boolean isExtension) {
		//Extensions don't list dependencies of core modules
		if (isExtension && SI_MODULES.contains(rm.getModuleId())) {
			return false;
		}
		if (excludeDerivativeModules && derivativeModules.contains(rm.getModuleId())) {
			return false;
		}
		
		if (blockListedModules != null && blockListedModules.contains(rm.getModuleId())) {
			return false;
		}
		
		return true;
	}

}