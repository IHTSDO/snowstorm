package com.kaicube.elasticversioncontrol.api;

public class PathUtil {

	public static final String SEPARATOR = "/";

	public static String getParentPath(String path) {
		final int indexOf = path.lastIndexOf(SEPARATOR);
		if (indexOf != -1) {
			return path.substring(0, indexOf);
		}
		return null;
	}

	public static String flaten(String path) {
		return path.replace("/", "_");
	}

	public static String fatten(String path) {
		return path.replace("_", "/");
	}
}
