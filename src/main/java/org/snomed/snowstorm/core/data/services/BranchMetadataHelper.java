package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.domain.Commit;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.snowstorm.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
/*
 * Helper to allow Snowstorm branch metadata with array and object values.
 */
public class BranchMetadataHelper {

	// Metadata for internal use only and can't be updated via branch metadata REST service
	public static final String INTERNAL_METADATA_KEY = "internal";
	private static final String COMMIT_METADATA_KEY_PREFIX = "commit.";
	private static final String DISABLE_CONTENT_AUTOMATIONS_TRANSIENT_METADATA_KEY = transientKey("disableContentAutomations");
	private static final String CLASSIFICATION_COMMIT_TRANSIENT_METADATA_KEY = transientKey("classificationCommit");

	@Autowired
	private ObjectMapper objectMapper;

	private static final String OBJECT_PREFIX = "{object}|";
	private final SimpleDateFormat lockMetadataDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	public static String transientKey(String key) {
		return COMMIT_METADATA_KEY_PREFIX + key;
	}

	public static void disableContentAutomations(Commit commit) {
		getInternal(commit).put(DISABLE_CONTENT_AUTOMATIONS_TRANSIENT_METADATA_KEY, "true");
	}

	public static boolean isContentAutomationsDisabled(Commit commit) {
		return isTrue(getInternal(commit).get(DISABLE_CONTENT_AUTOMATIONS_TRANSIENT_METADATA_KEY));
	}

	public static void classificationCommit(Commit commit) {
		getInternal(commit).put(CLASSIFICATION_COMMIT_TRANSIENT_METADATA_KEY, "true");
	}

	public static boolean isClassificationCommit(Commit commit) {
		return isTrue(getInternal(commit).get(CLASSIFICATION_COMMIT_TRANSIENT_METADATA_KEY));
	}


	private static boolean isTrue(String value) {
		return "true".equals(value);
	}

	private static Map<String, String> getInternal(Commit commit) {
		return commit.getBranch().getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY);
	}

	public static void clearTransientMetadata(Commit commit) {
		final Map<String, String> map = commit.getBranch().getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY);
		final Set<String> transientKeys = map.keySet().stream().filter(key -> key.startsWith(COMMIT_METADATA_KEY_PREFIX)).collect(Collectors.toSet());
		transientKeys.forEach(map::remove);
	}

	public String getBranchLockMetadata(String description) {
		Map<String, Object> lockMeta = new HashMap<>();
		lockMeta.put("creationDate", lockMetadataDateFormat.format(new Date()));
		Map<String, Object> lockContext = new HashMap<>();
		lockContext.put("userId", Optional.ofNullable(SecurityUtil.getUsername()).orElse(Config.SYSTEM_USERNAME));
		lockContext.put("description", description);
		lockMeta.put("context", lockContext);
		try {
			return OBJECT_PREFIX + objectMapper.writeValueAsString(lockMeta);
		} catch (JsonProcessingException e) {
			throw new RuntimeServiceException("Failed to serialise branch lock metadata", e);
		}
	}

}
