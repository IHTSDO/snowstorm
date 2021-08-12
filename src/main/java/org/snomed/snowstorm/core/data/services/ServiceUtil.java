package org.snomed.snowstorm.core.data.services;

public class ServiceUtil {

	public static void assertNotNull(String name, Object object) {
		if (object == null) {
			throw new RuntimeServiceException(String.format("%s must not be null!", name));
		}
	}

}
