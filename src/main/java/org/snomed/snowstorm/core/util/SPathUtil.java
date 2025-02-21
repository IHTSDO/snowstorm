package org.snomed.snowstorm.core.util;

import io.kaicode.elasticvc.api.PathUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Snowstorm PathUtil has domain specific concepts irrelevant to the ElasticVC library.
public class SPathUtil {
    /**
     * Return the given Branch path's ancestors. The given Branch path is not included in the ancestors,
     * and processing stops at the CodeSystem level, i.e. processing an Extension's Branch path will stop at MAIN/SNOMEDCT-XX.
     *
     * @param branchPath Find ancestors for this Branch path.
     * @return The given Branch path's ancestors.
     */
    public static List<String> getAncestors(String branchPath) {
        if (branchPath == null || !branchPath.startsWith("MAIN")) {
            throw new IllegalArgumentException("branchPath is invalid; cannot find ancestors.");
        }

        if (isCodeSystemBranch(branchPath)) {
            return Collections.emptyList();
        }

        List<String> ancestors = new ArrayList<>();
        String parent = PathUtil.getParentPath(branchPath);
        ancestors.add(parent);
        while (!isCodeSystemBranch(parent)) {
            parent = PathUtil.getParentPath(parent);
            ancestors.add(parent);
        }

        return ancestors;
    }

    public static boolean isCodeSystemBranch(String branchPath) {
        return branchPath.equals("MAIN") || branchPath.startsWith("SNOMEDCT-", branchPath.lastIndexOf("/") + 1);
    }
}
