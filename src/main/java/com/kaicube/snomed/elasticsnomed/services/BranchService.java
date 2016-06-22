package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.repositories.BranchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.kaicube.snomed.elasticsnomed.services.PathUtil.getParentPath;

@Service
public class BranchService {

	@Autowired
	private BranchRepository branchRepository;

	public Branch create(String path) {
		if (find(path) != null) {
			throw new IllegalArgumentException("Branch '" + path + "' already exists.");
		}
		final String parentPath = getParentPath(path);
		Date parentHead = null;
		if (parentPath != null) {
			final Branch parentBranch = find(parentPath);
			if (parentPath == null) {
				throw new IllegalStateException("Parent branch '" + parentPath + "' does not exist.");
			}
			parentHead = parentBranch.getHead();
		}

		final Branch branch = new Branch(path);
		branch.setBase(parentHead);
		if (parentHead != null) {
			branch.setHead(parentHead);
		}
		branch.setId(UUID.randomUUID().toString());
		return branchRepository.save(branch);
	}

	public void deleteAll() {
		branchRepository.deleteAll();
	}

	public void updateBranchHead(Branch branch, Date commit) {
		branch.setHead(commit);
		branchRepository.save(branch);
	}

	public Iterable<Branch> loadBranchAndAncestors(String path) {
		Set<String> branchPaths = getBranchPaths(PathUtil.flaten(path));
		return branchRepository.findByPathIn(branchPaths);
	}

	private Set<String> getBranchPaths(String path) {
		Set<String> branchPaths = new HashSet<>();
		String branchWork = path;
		branchPaths.add(branchWork);
		int lastIndexOf;
		while ((lastIndexOf = branchWork.lastIndexOf("_")) != -1) {
			branchWork = branchWork.substring(0, lastIndexOf);
			branchPaths.add(branchWork);
		}
		return branchPaths;
	}

	public Branch find(String path) {
		return branchRepository.findByPath(PathUtil.flaten(path));
	}

	public Iterable<Branch> findAll() {
		return branchRepository.findAll(new PageRequest(0, 10)).getContent();
	}
}
