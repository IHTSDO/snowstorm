package org.snomed.snowstorm.core.data.services;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;

@Service
/*
 * Service to create module dependency refset members as required, either temporarily
 * for export, or persisted eg during versioning.
 * 
 * Note we have a problem here that our dependency date can only be calculated if it's known to the 
 * code system, so for extensions on extensions, we'll need to find the parent branch and then
 * get THAT dependency date.
 */
public class ModuleDependencyService {
	
	public static String SCTID_MODULE_DEPENDENCY = "900000000000534007";
	public static int RECURSION_LIMIT = 100;
	
	public static String SOURCE_ET = "sourceEffectiveTime";
	public static String TARGET_ET = "targetEffectiveTime";
	public static Set<String> SI_MODULES = Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE);
	
	public static PageRequest LARGE_PAGE = PageRequest.of(0,10000);

	@Autowired
	private BranchService branchService;
	
	@Autowired
	private AuthoringStatsService statsService;
	
	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private ReferenceSetMemberService refsetService;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private long cacheValidAt = 0L;
	private Set<String> cachedInternationalModules;
	
	//Refresh when the HEAD time is greater than the HEAD time on MAIN
	//Check every 30 mins to save time when export operation actually called
	@Scheduled(fixedDelay = 1800000, initialDelay = 180000)
	public synchronized void refreshCache() {
		//Do we need to refresh the cache?
		Long currentTime = branchService.findBranchOrThrow(Branch.MAIN).getHeadTimestamp();
		if (currentTime > cacheValidAt) {
			//Map of components to Maps of ModuleId -> Counts, pull out all the 2nd level keys into a set
			Map<String,Map<String,Long>> moduleCountMap = statsService.getComponentCountsPerModule(Branch.MAIN);
			cachedInternationalModules= moduleCountMap.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
			cacheValidAt = currentTime;
			logger.info("MDR cache of International Modules refreshed for HEAD time: " + currentTime);
		}
	}
	
	public List<ReferenceSetMember> generateModuleDependencies(String branchPath, String effectiveDate, List<String> moduleFilter, boolean persist) {
		StopWatch sw = new StopWatch("MDRGeneration");
		sw.start();
		refreshCache();
		
		//What module dependency refset members already exist?
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet(SCTID_MODULE_DEPENDENCY);
		Page<ReferenceSetMember> rmPage = refsetService.findMembers(branchPath, searchRequest, LARGE_PAGE);
		Map<String, Set<ReferenceSetMember>> moduleMap = rmPage.getContent().stream()
				.collect(Collectors.groupingBy(ReferenceSetMember::getModuleId, Collectors.toSet()));
		
		//What modules are known to this code system?
		CodeSystem cs = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		
		//Do I have a dependency release?  If so, what's its effective time?
		boolean isInternational = true;
		Integer dependencyET = cs.getDependantVersionEffectiveTime();
		if (dependencyET == null) {
			dependencyET = Integer.parseInt(effectiveDate);
		} else {
			isInternational = false;
		}
		
		//What modules are actually present in the content?
		Map<String,Map<String,Long>> moduleCountMap = statsService.getComponentCountsPerModule(branchPath);
		Set<String> modulesRequired;
		if (moduleFilter != null && moduleFilter.size() > 0) {
			modulesRequired = new HashSet<>(moduleFilter);
		} else {
			modulesRequired = moduleCountMap.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
			
			//If we're not international, remove all international modules
			if (!isInternational) {
				modulesRequired.removeAll(cachedInternationalModules);
			}
		}
		
		//Recover all these module concepts to find out what module they themselves were defined in.
		//Generally, refset type modules are defined in the main extension module
		Map<String, String> moduleParentMap = getModuleParents(branchPath, new HashSet<>(modulesRequired), moduleCountMap, isInternational);
		
		//For extensions, the module with the concepts in it is assumed to be the parent, UNLESS
		//a module concept is defined in another module, in which case the owning module is the parent. 
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
			if (moduleParentMap.get(topLevelExtensionModule).contentEquals(topLevelExtensionModule)) {
				moduleParentMap.put(topLevelExtensionModule, Concepts.CORE_MODULE);
			}
		}
		
		//Remove any map entries that we don't need
		moduleMap.keySet().retainAll(modulesRequired);
		
		logger.info("Generating MDR for {}, modules [{}]", branchPath, String.join(", ", modulesRequired));
		
		//Update or augment module dependencies as required
		int recursionLimit = 0;
		for (String moduleId : modulesRequired) {
			boolean isExtensionMod = !cachedInternationalModules.contains(moduleId);
			boolean isExtensionTop = moduleId.equals(topLevelExtensionModule);
			
			Set<ReferenceSetMember> moduleDependencies = moduleMap.get(moduleId);
			if (moduleDependencies == null) {
				moduleDependencies = new HashSet<>();
				moduleMap.put(moduleId, moduleDependencies);
			}
			
			String thisLevel = moduleId;
			//Work out when we need to stop, depending on what we are
			while ( (isInternational && !thisLevel.equals(Concepts.MODEL_MODULE)) ||
					(isExtensionMod && (
							(isExtensionTop && !thisLevel.equals(Concepts.CORE_MODULE)) ||
							(!isExtensionTop && !thisLevel.equals(topLevelExtensionModule))
							))){
				thisLevel = updateOrCreateModuleDependency(moduleId, 
						thisLevel, 
						moduleParentMap, 
						moduleDependencies,
						effectiveDate,
						dependencyET,
						branchPath);
				if (++recursionLimit > RECURSION_LIMIT) {
					throw new IllegalStateException ("Recursion limit reached calculating MDR in " + branchPath + " for module " + thisLevel);
				}
			}
		}
		
		modulesRequired.addAll(SI_MODULES);
		sw.stop();
		logger.info("MDR generation for {}, modules [{}] took {}s", branchPath, String.join(", ", modulesRequired), sw.getTotalTimeSeconds());
		return moduleMap.values().stream()
				.flatMap(Set::stream)
				/*.filter(rm -> modulesRequired.contains(rm.getModuleId()))*/
				.filter(rm -> rm.getEffectiveTime().equals(effectiveDate))
				.collect(Collectors.toList());
	}

	private String updateOrCreateModuleDependency(String moduleId, String thisLevel, Map<String, String> moduleParents,
			Set<ReferenceSetMember> moduleDependencies, String effectiveDate, Integer dependencyET, String branchPath) {
		//Take us up a level
		String nextLevel = moduleParents.get(thisLevel);
		if (nextLevel == null) {
			throw new IllegalStateException("Unable to calculate module dependency via parent module of " + thisLevel + " in " + branchPath + " due to no parent mapped for module " + thisLevel);
		}
		ReferenceSetMember rm = findOrCreateModuleDependency(moduleId, moduleDependencies, nextLevel);
		moduleDependencies.add(rm);
		rm.setEffectiveTimeI(Integer.parseInt(effectiveDate));
		rm.setAdditionalField(SOURCE_ET, effectiveDate);
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
		return rm;
	}

	private Map<String, String> getModuleParents(String branchPath, Set<String> moduleIds, Map<String, Map<String, Long>> moduleCountMap, boolean isInternational) {
		Map<String, String> moduleParentMap = new HashMap<>();
		List<Long> conceptIds = moduleIds.stream().map(m -> Long.parseLong(m)).collect(Collectors.toList());
		int recursionDepth = 0;
		//Repeat lookup of parents until all modules encountered are populated in the map
		while (conceptIds.size() > 0) {
			Page<Concept> modulePage = conceptService.find(conceptIds, null, branchPath, LARGE_PAGE);
			if (modulePage.getContent().size() != conceptIds.size()) {
				throw new IllegalStateException ("Failed to find expected " + moduleIds.size() + " modules in " + branchPath);
			}
			Map<String, String> partialParentMap = modulePage.getContent().stream()
				.collect(Collectors.toMap(c -> c.getId(), c -> c.getModuleId()));
			moduleParentMap.putAll(partialParentMap);
			
			//Now look up all these new parents as well, unless we've already got them in our map
			conceptIds = partialParentMap.values().stream()
					.filter(m -> !moduleParentMap.containsKey(m))
					.map(m -> Long.parseLong(m))
					.collect(Collectors.toList());
			
			if (++recursionDepth > RECURSION_LIMIT) {
				throw new IllegalStateException("Recursion depth reached looking for module parents in " + branchPath + " with " + String.join(",", moduleIds));
			}
		}
		
		//Also always ensure that our two international modules are populated
		moduleParentMap.put(Concepts.CORE_MODULE, Concepts.MODEL_MODULE);
		return moduleParentMap;
	}
}
