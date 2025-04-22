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
import org.springframework.util.CollectionUtils;
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
	
	public Set<String> SI_MODULES = new HashSet<>(Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE, Concepts.ICD10_MODULE, Concepts.ICD11_MODULE));
	
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
			
			//During unit tests, or in non-standard installations we might not see the ICD-10 and ICD-11 Modules
			if (!cachedInternationalModules.contains(Concepts.ICD10_MODULE)) {
				SI_MODULES.remove(Concepts.ICD10_MODULE);
			}
			if (!cachedInternationalModules.contains(Concepts.ICD11_MODULE)) {
				SI_MODULES.remove(Concepts.ICD11_MODULE);
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
	public Set<ReferenceSetMember> generateModuleDependencies(String branchPath, String effectiveDate, Set<String> modulesIncluded, boolean isDelta, Commit commit) {
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
		Branch branch = branchService.findBranchOrThrow(branchPath, true);  //Include Inherited Metadata
		
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
		
		//Having said that, NZ for example defines a module with no content
		//So add modules from existing active MDRS members
		Set<String> activeModules = rmPage.getContent().stream()
				.filter(SnomedComponent::isActive)
				.map(SnomedComponent::getModuleId)
				.collect(Collectors.toSet());
		modulesRequired.addAll(activeModules);
		
		//And also as seen in NZ, make a particular note of existing mutual dependencies
		Map<String, Set<String>> mutualDependencies = detectMutualDependencies(rmPage.getContent());
		modulesRequired.addAll(mutualDependencies.keySet());
		
		//If we're not an Edition, remove all international modules, but keep the Derivative modules
		if (!isEdition) {
			for (String module : cachedInternationalModules) {
				if (modulesIncluded == null || !modulesIncluded.contains(module)) {
					modulesRequired.remove(module);
				}
			}
		}
		
		//Recover all these module concepts to find out what module they themselves were defined in.
		//Generally, refset type modules are defined in the main extension module
		Map<String, String> moduleHierarchyMap = getModuleOfModule(branchPath, new HashSet<>(modulesRequired));
		if (!isInternational) {
			mapTopLevelExtensionModuleToCore(moduleCountMap, moduleHierarchyMap, moduleMap, mutualDependencies);
		}

		//Remove any map entries that we don't need
		moduleMap.keySet().retainAll(modulesRequired);
		logger.info("Generating MDRS for {}, {} modules [{}]", branchPath, isInternational ? "international" : "extension", String.join(", ", modulesRequired));
		
		//Update or augment module dependencies as required
		for (String moduleId : modulesRequired) {
			boolean isExtensionMod = !cachedInternationalModules.contains(moduleId);

			Set<ReferenceSetMember> moduleDependencies = moduleMap.computeIfAbsent(moduleId, k -> new HashSet<>());

			if (isInternational && isExtensionMod) {
				logger.warn("CHECK LOGIC: {} thought to be both International and an Extension Module", moduleId);
			}
			
			String thisLevel = moduleId;
			int recursionLimit = 0;
			while (!thisLevel.equals(Concepts.MODEL_MODULE)){
				thisLevel = updateOrCreateModuleDependency(moduleId, 
						thisLevel, 
						moduleHierarchyMap, 
						moduleDependencies,
						effectiveDate,
						dependencyET,
						branchPath,
						isInternational,
						mutualDependencies);
				if (++recursionLimit > RECURSION_LIMIT) {
					logger.warn("Recursion limit reached calculating MDRS in " + branchPath + " for module " + thisLevel + ". Falling back to assume dependency on Core Module");
					Map<String, String> assumptionMap = new HashMap<>();
					assumptionMap.put(thisLevel, Concepts.CORE_MODULE);
					updateOrCreateModuleDependency(moduleId, 
							thisLevel, 
							assumptionMap, 
							moduleDependencies,
							effectiveDate,
							dependencyET,
							branchPath,
							isInternational,
							mutualDependencies);
					break;
				}
			}
		}

		sw.stop();
		logger.info("MDRS generation for {}, modules [{}] took {}s", branchPath, String.join(", ", modulesRequired), sw.getTotalTimeSeconds());
		Set<ReferenceSetMember> mdrMembers = moduleMap.values().stream()
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
		
		//For delta and update we're only interested in those records that have changed
		Set<ReferenceSetMember> updatedMDRmembers = mdrMembers.stream()
				.filter(rm -> rm.getEffectiveTime().equals(effectiveDate))
				.collect(Collectors.toSet());
		
		if (commit != null) {
			doSaveBatchComponents(updatedMDRmembers, commit, ReferenceSetMember.Fields.MEMBER_ID, memberRepository);
		}
		
		return isDelta ? updatedMDRmembers : mdrMembers;
	}

	private Map<String, Set<String>> detectMutualDependencies(List<ReferenceSetMember> mdrsMembers) {
		Map<String, Set<String>> mutualDependencies = new HashMap<>();
		for (ReferenceSetMember rm : mdrsMembers) {
			//Check through all other active members to see if the target also references our source
			if (rm.isActive()) {
				String sourceModule = rm.getModuleId();
				String targetModule = rm.getReferencedComponentId();
				if (mdrsMembers.stream()
						.filter(SnomedComponent::isActive)
						.filter(rm2 -> rm2.getModuleId().equals(targetModule))
						.anyMatch(rm2 -> rm2.getReferencedComponentId().contentEquals(sourceModule))) {
					populateMutualDependency(mutualDependencies, sourceModule, targetModule);
					populateMutualDependency(mutualDependencies, targetModule, sourceModule);
				}
			}
		}
		if (!mutualDependencies.isEmpty()) {
			logger.debug("Detected mutual dependencies between modules:" + mutualDependencies.toString());
		}
		return mutualDependencies;
	}

	private void populateMutualDependency(Map<String, Set<String>> mutualDependencies, String sourceModule,
			String targetModule) {
        Set<String> dependencies = mutualDependencies.computeIfAbsent(sourceModule, k -> new HashSet<>());
        dependencies.add(targetModule);
	}

	private void mapTopLevelExtensionModuleToCore(Map<String, Map<String, Long>> moduleCountMap,
			Map<String, String> moduleHierarchyMap, Map<String, Set<ReferenceSetMember>> moduleMap, Map<String, Set<String>> mutualDependencies) {
		//If we have multiple modules that are defined in themselves, then we must have existing 
		//records supplied
		List<String> selfMappedModules = getSelfMappedModules(moduleHierarchyMap);
		if (selfMappedModules.size() > 1) {
			for (String module : selfMappedModules) {
				moduleHierarchyMap.put(module, determineClosestParentFromExistingHierarchy(module, selfMappedModules, moduleMap));
			}
			//Did we get them all?
			if (getSelfMappedModules(moduleHierarchyMap).size() == 0) {
				return;
			}
		}
		//Safest alternative solution is if only one extension module is currently mapped to itself
		boolean firstSolutionSuccess = true;
		String selfMappedModule = null;
		for (Map.Entry<String, String> moduleMapping : moduleHierarchyMap.entrySet()) {
			if (moduleMapping.getKey().equals(moduleMapping.getValue())) {
				//Is this the first one encountered, or do we need to try the other solution?
				if (selfMappedModule == null) {
					selfMappedModule = moduleMapping.getKey();
				} else {
					firstSolutionSuccess = false;
					break;
				}
			}
		}
		
		if (firstSolutionSuccess && selfMappedModule != null) {
			moduleHierarchyMap.put(selfMappedModule, Concepts.CORE_MODULE);
			//If this module has mutual dependencies, they will also all be dependent on the core module
			if (mutualDependencies.containsKey(selfMappedModule)) {
				for (String mutalDependency : mutualDependencies.get(selfMappedModule)) {
					moduleHierarchyMap.put(mutalDependency, Concepts.CORE_MODULE);
				}
			}
			return;
		}
		
		//Otherwise we'll hope that the default module is the one with the most concepts
		String topLevelExtensionModule = null;
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

	private String determineClosestParentFromExistingHierarchy(String module, List<String> selfMappedModules,
			Map<String, Set<ReferenceSetMember>> moduleMap) {
		
		List<String> ancestors = findAncestorModules(module, selfMappedModules, moduleMap.get(module));
		//If we only see a single ancestor, that's our closest parent
		if (ancestors.isEmpty()) {
			return module;
		} else if (ancestors.size() == 1) {
			return ancestors.get(0);
		} else {
			//If we have a few, then we need to work out which one of those has the most ancestors itself
			//to be the closest parent
			int maxAncestors = 0;
			String lowestLevelAncestor = null;
			for (String ancestor : ancestors) {
				int ancestorCount = findAncestorModules(ancestor, ancestors, moduleMap.get(ancestor)).size();
				if (ancestorCount > maxAncestors) {
					maxAncestors = ancestorCount;
					lowestLevelAncestor = ancestor;
				}
			}
			return lowestLevelAncestor;
		}
	}

	private List<String> findAncestorModules(String module, List<String> targetSet,
			Set<ReferenceSetMember> existingRefsetMembers) {
		Set<String> ancestors = existingRefsetMembers.stream()
				.filter(SnomedComponent::isActive)
				.map(ReferenceSetMember::getReferencedComponentId)
				.filter(targetSet::contains)
				.collect(Collectors.toSet());
		return new ArrayList<>(ancestors);
	}

	private List<String> getSelfMappedModules(Map<String, String> moduleHierarchyMap) {
		return new ArrayList<>(moduleHierarchyMap.keySet().stream()
				.filter(m -> moduleHierarchyMap.get(m).equals(m))
				.collect(Collectors.toSet()));
	}

	private String updateOrCreateModuleDependency(String moduleId, String thisLevel, Map<String, String> moduleParents,
			Set<ReferenceSetMember> moduleDependencies, String effectiveDate, Integer dependencyET, String branchPath, boolean isInternational, Map<String, Set<String>> mutualDependencies) {
		//Take us up a level
		String nextLevel = moduleParents.get(thisLevel);
		if (nextLevel == null) {
			throw new IllegalStateException("Unable to calculate module dependency via parent module of " + thisLevel + " in " + branchPath + " due to no parent mapped for module " + thisLevel);
		}
		
		findOrCreateModuleDependency(moduleId, moduleDependencies, nextLevel, isInternational, effectiveDate, dependencyET);
		
		//Now if this module is mutually dependent on other modules, they should be included at the same time, since we won't encounter them in a hierarchy
		if (mutualDependencies.get(moduleId) != null) {
			for (String mutuallyDependentModule : mutualDependencies.get(moduleId)) {
				findOrCreateModuleDependency(moduleId, moduleDependencies, mutuallyDependentModule, isInternational, effectiveDate, dependencyET);
			}
		}
		
		return nextLevel;
	}

	//TODO Look out for inactive reference set members and use active by preference
	//or reactive inactive if required.
	private void findOrCreateModuleDependency(String moduleId, Set<ReferenceSetMember> moduleDependencies,
			String targetModuleId, boolean isInternational, String effectiveDate, Integer dependencyET) {
		ReferenceSetMember rm = null;
		
		for (ReferenceSetMember thisMember : moduleDependencies) {
			if (thisMember.getReferencedComponentId().equals(targetModuleId)) {
				rm = thisMember;
				break;
			}
		}
		
		if (rm == null) {
			//Create if not found
			rm = new ReferenceSetMember();
			rm.setMemberId(UUID.randomUUID().toString());
			rm.setModuleId(moduleId);
			rm.setReferencedComponentId(targetModuleId);
			rm.setActive(true);
			rm.setCreating(true);
		}
		
		//Add to our list and configure appropriate dates
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
		if (cachedInternationalModules.contains(targetModuleId)) {
			rm.setAdditionalField(TARGET_ET, dependencyET.toString());
		} else {
			rm.setAdditionalField(TARGET_ET, effectiveDate);
		}
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
		//Repeat lookup of parents (that is, the module they are declared in) until all modules encountered are populated in the map
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
					Set<String> found = modulePage.getContent().stream().map(Concept::getId).collect(Collectors.toSet());
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

	public boolean isExportable(ReferenceSetMember rm, boolean isExtension, Set<String> modulesIncluded) {
		if (!CollectionUtils.isEmpty(modulesIncluded) && modulesIncluded.contains(rm.getModuleId())) {
			return true;
		}

		//Extensions don't list dependencies of core modules
		if (isExtension && SI_MODULES.contains(rm.getModuleId())) {
			return false;
		}
		if (excludeDerivativeModules && derivativeModules.contains(rm.getModuleId())) {
			return false;
		}

        return blockListedModules == null || !blockListedModules.contains(rm.getModuleId());
    }

}