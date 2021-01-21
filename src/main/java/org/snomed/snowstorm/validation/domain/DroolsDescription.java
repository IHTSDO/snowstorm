package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.Description;
import org.snomed.snowstorm.core.data.domain.Concepts;

import java.util.HashMap;
import java.util.Map;

public class DroolsDescription implements Description {

	private final org.snomed.snowstorm.core.data.domain.Description description;
	private Map<String, String> acceptabilityMapWithIds;

	public DroolsDescription(org.snomed.snowstorm.core.data.domain.Description description) {
		this.description = description;

		Map<String, String> acceptabilityMapWithConstants = description.getAcceptabilityMap();
		acceptabilityMapWithIds = new HashMap<>();
		for (String key : acceptabilityMapWithConstants.keySet()) {
			String acceptabilityIdConstant = acceptabilityMapWithConstants.get(key);
			String acceptabilityId = Concepts.descriptionAcceptabilityNames.inverse().get(acceptabilityIdConstant);
			if (acceptabilityId != null) {
				acceptabilityMapWithIds.put(key, acceptabilityId);
			}
		}
	}

	public String getReleaseHash() {
		return description.getReleaseHash();
	}

	@Override
	public Map<String, String> getAcceptabilityMap() {
		return acceptabilityMapWithIds;
	}

	@Override
	public String getConceptId() {
		return description.getConceptId();
	}

	@Override
	public String getLanguageCode() {
		return description.getLanguageCode();
	}

	@Override
	public String getTypeId() {
		return description.getTypeId();
	}

	@Override
	public String getTerm() {
		return description.getTerm();
	}

	@Override
	public String getCaseSignificanceId() {
		return description.getCaseSignificanceId();
	}

	@Override
	public boolean isTextDefinition() {
		return description.getTypeId().equals(Concepts.TEXT_DEFINITION);
	}

	@Override
	public String getId() {
		return description.getId();
	}

	@Override
	public boolean isActive() {
		return description.isActive();
	}

	@Override
	public boolean isPublished() {
		return description.getEffectiveTimeI() != null;
	}

	@Override
	public boolean isReleased() {
		return description.isReleased();
	}

	@Override
	public String getModuleId() {
		return description.getModuleId();
	}
}
