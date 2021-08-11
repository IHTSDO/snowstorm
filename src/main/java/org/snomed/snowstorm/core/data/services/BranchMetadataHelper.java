package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.snowstorm.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
/*
 * Helper to allow Snowstorm branch metadata with array and object values.
 */
public class BranchMetadataHelper {

	// Metadata for internal use only and can't be updated via branch metadata REST service
	public static final String INTERNAL_METADATA_KEY = "internal";

	public static final String AUTHOR_FLAGS_METADATA_KEY = "authorFlags";

	@Autowired
	private ObjectMapper objectMapper;

	private static final String OBJECT_PREFIX = "{object}|";
	private final SimpleDateFormat lockMetadataDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

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
