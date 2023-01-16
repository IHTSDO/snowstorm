package org.snomed.snowstorm.core.data.services.identifier;

import org.springframework.util.StringUtils;

public class IdentifierHelper extends org.ihtsdo.drools.helper.IdentifierHelper {

	public static boolean isExpressionId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && "6".equals(getPartitionIdPart(sctid));
	}

	public static int getNamespaceFromSCTID(String sctid) {
		if (sctid.charAt(sctid.length() - 3) == '0') {
			return 0;
		} else {
			return Integer.parseInt(sctid.substring(sctid.length() - 10, sctid.length() - 3));
		}
	}

	private static String getPartitionIdPart(String sctid) {
		return !StringUtils.isEmpty(sctid) && sctid.length() > 4 ? sctid.substring(sctid.length() - 2, sctid.length() - 1) : null;
	}

}
