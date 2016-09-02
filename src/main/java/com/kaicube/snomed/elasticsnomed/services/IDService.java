package com.kaicube.snomed.elasticsnomed.services;

public class IDService {

	// TODO - CIS Integration

	public static String getHackId() {
		return "" + Math.round(Math.random() * 1000000000f);
	}

}
