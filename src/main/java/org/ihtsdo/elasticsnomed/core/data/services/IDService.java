package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.base.Strings;

import java.util.regex.Pattern;

public class IDService {

	public static final Pattern SCTID_PATTERN = Pattern.compile("\\d{7,18}");

	// TODO - CIS Integration

	public static String getHackId() {
		String id;
		while ((id = "" + Math.round(Math.random() * 100000000000000000f)).length() < 15) {
		}
		return id.substring(0, 15);
	}

	public static boolean isConceptId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && "0".equals(getPartitionIdPart(sctid));
	}

	public static boolean isDescriptionId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && "1".equals(getPartitionIdPart(sctid));
	}

	private static String getPartitionIdPart(String sctid) {
		if (!Strings.isNullOrEmpty(sctid) && sctid.length() > 4) {
			return sctid.substring(sctid.length() - 2, sctid.length() - 1);
		}
		return null;
	}
}
