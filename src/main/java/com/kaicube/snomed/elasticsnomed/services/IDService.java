package com.kaicube.snomed.elasticsnomed.services;

public class IDService {

	// TODO - CIS Integration

	public static String getHackId() {
		return ("" + Math.round(Math.random() * 100000000000f)).substring(0, 9);
	}

}
