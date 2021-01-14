package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.*;

/**
 * Represents an active concept with fields to assist logical searching.
 */
@Document(indexName = "semantic")
public class QueryConcept extends DomainEntity<QueryConcept> {

	public static final String ATTR_TYPE_WILDCARD = "all";

	public static final String ATTR_NUMERIC_TYPE_WILDCARD = "all_numeric";

	public interface Fields {
		String CONCEPT_ID_FORM = "conceptIdForm";
		String CONCEPT_ID = "conceptIdL";
		String PARENTS = "parents";
		String ANCESTORS = "ancestors";
		String STATED = "stated";
		String ATTR = "attr";
		String ATTR_MAP = "attrMap";
	}
	@Field(type = FieldType.Keyword)
	private String conceptIdForm;

	@Field(type = FieldType.Long, store = true)
	private Long conceptIdL;

	@Field(type = FieldType.Long)
	private Set<Long> parents;

	@Field(type = FieldType.Long)
	private Set<Long> ancestors;

	@Field(type = FieldType.Boolean)
	private boolean stated;

	@Field(type = FieldType.Object)
	private Map<String, Set<Object>> attr;

	@Field(type = FieldType.Keyword, index = false, store = true)
	// Format:
	// groupNo:attr=value:attr=value,value|groupNo:attr=value:attr=value,value
	private String attrMap;

	@Transient
	private Map<Integer, Map<String, List<String>>> groupedAttributesMap;

	public QueryConcept() {
	}

	public QueryConcept(Long conceptId, Set<Long> parentIds, Set<Long> ancestorIds, boolean stated) {
		this.conceptIdL = conceptId;
		this.parents = parentIds;
		this.ancestors = ancestorIds;
		this.stated = stated;
		updateConceptIdForm();
	}

	public void clearAttributes() {
		if (groupedAttributesMap != null) {
			groupedAttributesMap.clear();
		}
	}

	public void addAttribute(int group, Long type, String value) {
		if (groupedAttributesMap == null) {
			groupedAttributesMap = new HashMap<>();
		}
		groupedAttributesMap.computeIfAbsent(group, (g) -> new HashMap<>())
				.computeIfAbsent(type.toString(), (t) -> new ArrayList<>()).add(value);
	}


	public void removeAttribute(int group, Long type, String value) {
		if (groupedAttributesMap == null) {
			groupedAttributesMap = new HashMap<>();
		}
		Map<String, List<String>> groupAttributes = groupedAttributesMap.get(group);
		if (groupAttributes != null) {

			List<String> typeValues = groupAttributes.get(type.toString());
			if (typeValues != null) {
				typeValues.remove(value.toString());
				if (typeValues.isEmpty()) {
					groupAttributes.remove(type.toString());
				}
			}

			if (groupAttributes.isEmpty()) {
				groupedAttributesMap.remove(group);
			}
		}
	}

	@JsonIgnore
	public Map<Integer, Map<String, List<String>>> getGroupedAttributesMap() {
		if (groupedAttributesMap == null && this.attrMap != null) {
			return GroupedAttributesMapSerializer.deserializeMap(this.attrMap);
		}
		return groupedAttributesMap;
	}

	public Map<String, Set<Object>> getAttr() {
		return GroupedAttributesMapSerializer.serializeFlatMap(getGroupedAttributesMap());
	}

	public void setAttr(Map attr) {
		this.attr = attr;
	}

	public String getAttrMap() {
		if (this.attrMap == null) {
			return GroupedAttributesMapSerializer.serializeMap(getGroupedAttributesMap());
		}
		return this.attrMap;
	}

	public void serializeGroupedAttributesMap() {
		setAttrMap(GroupedAttributesMapSerializer.serializeMap(getGroupedAttributesMap()));
		setAttr(GroupedAttributesMapSerializer.serializeFlatMap(getGroupedAttributesMap()));
	}

	public void setAttrMap(String attrMap) {
		this.attrMap = attrMap;
	}

	private void updateConceptIdForm() {
		this.conceptIdForm = toConceptIdForm(conceptIdL, stated);
	}

	public static String toConceptIdForm(Long conceptId, boolean stated) {
		return conceptId + (stated ? "_s" : "_i");
	}

	@Override
	public boolean isChanged() {
		return true;
	}

	@Override
	public String getId() {
		return conceptIdForm;
	}

	@Override
	public boolean isComponentChanged(QueryConcept that) {
		return that == null
				|| !ancestors.equals(that.ancestors);
	}

	public Long getConceptIdL() {
		return conceptIdL;
	}

	public Set<Long> getParents() {
		return parents;
	}

	public Set<Long> getAncestors() {
		return ancestors;
	}

	public boolean isStated() {
		return stated;
	}

	public void setConceptIdForm(String conceptIdForm) {
		this.conceptIdForm = conceptIdForm;
	}

	public String getConceptIdForm() {
		return conceptIdForm;
	}

	public QueryConcept setConceptIdL(Long conceptIdL) {
		this.conceptIdL = conceptIdL;
		return this;
	}

	public void setParents(Set<Long> parents) {
		this.parents = parents;
	}

	public void setAncestors(Set<Long> ancestors) {
		this.ancestors = ancestors;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
	}

	@Override
	public String toString() {
		return "QueryConcept{" +
				"conceptIdForm=" + conceptIdForm +
				", parents=" + parents +
				", ancestors=" + ancestors +
				", stated=" + stated +
				", attrMap=" + getAttrMap() +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		QueryConcept that = (QueryConcept) o;
		return stated == that.stated &&
				Objects.equals(conceptIdL, that.conceptIdL);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptIdL, stated);
	}

	private static final class GroupedAttributesMapSerializer {

		private static String serializeMap(Map<Integer, Map<String, List<String>>> groupedAttributesMap) {
			if (groupedAttributesMap == null) {
				return "";
			}
			final StringBuilder builder = new StringBuilder();
			for (Integer groupNo : groupedAttributesMap.keySet()) {
				Map<String, List<String>> attributes = groupedAttributesMap.get(groupNo);
				builder.append(groupNo);
				builder.append(":");
				for (String type : attributes.keySet()) {
					builder.append(type);
					builder.append("=");
					for (String value : attributes.get(type)) {
						builder.append(value);
						builder.append(",");
					}
					deleteLastCharacter(builder);
					builder.append(":");
				}
				deleteLastCharacter(builder);
				builder.append("|");
			}
			deleteLastCharacter(builder);
			return builder.toString();
		}

		private static StringBuilder deleteLastCharacter(StringBuilder builder) {
			if (builder.length() > 0) {
				builder.deleteCharAt(builder.length() - 1);
			}
			return builder;
		}

		private static Map<Integer, Map<String, List<String>>> deserializeMap(String attrMap) {
			Map<Integer, Map<String, List<String>>> groupedAttributesMap = new HashMap<>();
			if (attrMap.isEmpty()) {
				return groupedAttributesMap;
			}
			String[] groups = attrMap.split("\\|");
			for (String group : groups) {
				String[] attributes = group.split(":");
				int groupNo = Integer.parseInt(attributes[0]);
				Map<String, List<String>> attributeMap = new HashMap<>();
				for (int i = 1; i < attributes.length; i++) {
					String attribute = attributes[i];
					String[] attrParts = attribute.split("=");
					String type = attrParts[0];
					String[] values = attrParts[1].split(",");
					attributeMap.put(type, Arrays.asList(values));
				}
				groupedAttributesMap.put(groupNo, attributeMap);
			}
			return groupedAttributesMap;
		}

		private static List<Object> checkAndTransformConcreteValues(List<String> values) {
			List<Object> transformed = new ArrayList<>();
			for (String value : values) {
				if (value.startsWith("#")) {
					String numeric = value.substring(1);
					if (!NumberUtils.isParsable(numeric)) {
						throw new IllegalArgumentException(String.format("%s is not a valid number", numeric));
					}
					// number
					long longValue = NumberUtils.toLong(numeric);
					if (longValue != 0) {
						transformed.add(longValue);
					} else {
						float floatValue = NumberUtils.toFloat(numeric, -1.0f);
						if (floatValue != -1.0f) {
							transformed.add(floatValue);
						}
					}
				} else {
					transformed.add(value);
				}
			}
			return transformed;
		}

		private static Map<String, Set<Object>> serializeFlatMap(Map<Integer, Map<String, List<String>>> groupedAttributesMap) {
			Map<String, Set<Object>> attributesMap = new HashMap<>();
			Set<Object> allValues = new HashSet<>();
			Set<Object> allNumericValues = new HashSet<>();
			if (groupedAttributesMap != null) {
				groupedAttributesMap.forEach((group, attributes) -> {
					attributes.forEach((type, values) -> {
						Set<Object> valueList = attributesMap.computeIfAbsent(type, (t) -> new HashSet<>());
						List<Object> converted = checkAndTransformConcreteValues(values);
						// add numeric fields for concrete values with #
						Object numericValue = converted.stream().filter(v -> !(v instanceof String)).findFirst().orElse(null);
						if (numericValue != null) {
							valueList.addAll(converted);
							allNumericValues.addAll(converted);
						} else {
							valueList.addAll(values);
							allValues.addAll(values);
						}
					});
				});
			}
			attributesMap.put(ATTR_TYPE_WILDCARD, allValues);
			if (!allNumericValues.isEmpty()) {
				attributesMap.put(ATTR_NUMERIC_TYPE_WILDCARD, allNumericValues);
			}
			return attributesMap;
		}
	}
}
