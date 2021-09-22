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
import org.springframework.stereotype.Service;

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
	
	public static String SOURCE_ET = "sourceEffectiveTime";
	public static String TARGET_ET = "targetEffectiveTime";
	public static Set<String> SI_MODULES = Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE);
	
	public static PageRequest UNLIMITED = PageRequest.of(0,Integer.MAX_VALUE);

	@Autowired
	private AuthoringStatsService statsService;
	
	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private ReferenceSetMemberService refsetService;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	public List<ReferenceSetMember> generateModuleDependencies(String branchPath, String effectiveDate, List<String> moduleFilter, boolean persist) {
		//What module dependency refset members already exist?
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		Page<ReferenceSetMember> rmPage = refsetService.findMembers(branchPath, searchRequest, UNLIMITED);
		Map<String, Set<ReferenceSetMember>> moduleMap = rmPage.getContent().stream()
				.collect(Collectors.groupingBy(ReferenceSetMember::getModuleId, Collectors.toSet()));
		
		//What modules are known to this code system?
		CodeSystem cs = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		
		//Do I have a dependency release?  If so, what's its effective time?
		Integer dependencyET = cs.getDependantVersionEffectiveTime();
		
		//What modules are actually present in the content, or are we working with a filtered list?
		Set<String> modulesRequired = getModulesRequired(branchPath, moduleFilter);
		
		//Recover all these module concepts to find out what module they themselves were defined in
		Map<String, String> moduleParents = getModuleParents(branchPath, new HashSet<>(modulesRequired));
		
		//Remove any map entries that we don't need
		moduleMap.keySet().retainAll(modulesRequired);
		
		//Update or augment module dependencies as required
		for (String moduleId : modulesRequired) {
			Set<ReferenceSetMember> moduleDependencies = moduleMap.get(moduleId);
			if (moduleDependencies == null) {
				moduleDependencies = new HashSet<>();
				moduleMap.put(moduleId, moduleDependencies);
			}
			
			String thisLevel = moduleId;
			while (!thisLevel.equals(Concepts.MODEL_MODULE)) {
				thisLevel = updateOrCreateModuleDependency(moduleId, 
						thisLevel, 
						moduleParents, 
						moduleDependencies,
						effectiveDate,
						dependencyET,
						branchPath);
			}
		}
		return rmPage.getContent();
	}

	private String updateOrCreateModuleDependency(String moduleId, String thisLevel, Map<String, String> moduleParents,
			Set<ReferenceSetMember> moduleDependencies, String effectiveDate, Integer dependencyET, String branchPath) {
		//Take us up a level
		String nextLevel = moduleParents.get(thisLevel);
		if (nextLevel == null) {
			throw new IllegalStateException("Unable to calculate module dependency via parent module of " + thisLevel + " in " + branchPath);
		}
		ReferenceSetMember rm = findOrCreateModuleDependency(moduleId, moduleDependencies, nextLevel);
		moduleDependencies.add(rm);
		rm.setEffectiveTimeI(Integer.parseInt(effectiveDate));
		rm.setAdditionalField(SOURCE_ET, effectiveDate);
		//Now is this module part of our dependency, or is it unique part of this CodeSystem?
		//For now, we'll assume the International Edition is the dependency
		if (SI_MODULES.contains(thisLevel)) {
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

	private Set<String> getModulesRequired(String branchPath, List<String> moduleFilter) {
		if (moduleFilter != null && moduleFilter.size() > 0) {
			return new HashSet<>(moduleFilter);
		}
		
		Map<String,Map<String,Long>> moduleCountMap = statsService.getComponentCountsPerModule(branchPath);
		return moduleCountMap.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
	}

	private Map<String, String> getModuleParents(String branchPath, Set<String> moduleIds) {
		Map<String, String> moduleParentMap = new HashMap<>();
		List<Long> conceptIds = moduleIds.stream().map(m -> Long.parseLong(m)).collect(Collectors.toList());
		int recursionDepth = 0;
		//Repeat lookup of parents until all modules encountered are populated in the map
		while (conceptIds.size() > 0) {
			Page<Concept> modulePage = conceptService.find(conceptIds, null, branchPath, UNLIMITED);
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
			
			if (++recursionDepth > 10) {
				throw new IllegalStateException("Recursion depth reached looking for module parents in " + branchPath + " with " + String.join(",", moduleIds));
			}
		}
		return moduleParentMap;
	}
}
