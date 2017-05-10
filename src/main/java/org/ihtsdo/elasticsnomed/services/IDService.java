package org.ihtsdo.elasticsnomed.services;

import com.google.common.base.Strings;

public class IDService {

	// TODO - CIS Integration

	public static String getHackId() {
		String id;
		while ((id = "" + Math.round(Math.random() * 100000000000000f)).length() < 9) {
		}
		return id.substring(0, 9);
	}

	public static boolean isConceptId(String sctid) {
		return "0".equals(getPartitionIdPart(sctid));
	}

	public static boolean isDescriptionId(String sctid) {
		return "1".equals(getPartitionIdPart(sctid));
	}

	private static String getPartitionIdPart(String sctid) {
		if (!Strings.isNullOrEmpty(sctid) && sctid.length() > 4) {
			return sctid.substring(sctid.length() - 2, sctid.length() - 1);
		}
		return null;
	}
}
