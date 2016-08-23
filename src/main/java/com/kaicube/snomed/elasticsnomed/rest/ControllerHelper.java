package com.kaicube.snomed.elasticsnomed.rest;

public class ControllerHelper {

	public static String parseBranchPath(String branch) {
		return branch.replace("|", "/");
	}
}
