package org.snomed.snowstorm.core.data.services.identifier;

public class IdentifierHelper extends org.ihtsdo.drools.helper.IdentifierHelper {

	public static int getNamespaceFromSCTID(String sctid) {
		if (sctid.charAt(sctid.length() - 3) == '0') {
			return 0;
		} else {
			return Integer.parseInt(sctid.substring(sctid.length() - 10, sctid.length() - 3));
		}
	}

}
