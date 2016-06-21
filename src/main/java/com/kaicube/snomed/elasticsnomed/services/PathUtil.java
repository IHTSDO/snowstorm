package com.kaicube.snomed.elasticsnomed.services;

public class PathUtil {

	public static final String SEPARATOR = "_";

	public static String getParentPath(String path) {
		final int indexOf = path.lastIndexOf(SEPARATOR);
		if (indexOf != -1) {
			return path.substring(0, indexOf);
		}
		return null;
	}
}
