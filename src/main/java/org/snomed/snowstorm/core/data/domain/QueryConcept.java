package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.apache.commons.lang3.math.NumberUtils;
import org.snomed.snowstorm.fhir.domain.FHIRGraphNode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Represents an active concept with fields to assist logical searching.
 */
@Document(indexName = "#{@indexNameProvider.indexName('semantic')}", createIndex = false)
public class QueryConcept extends DomainEntity<QueryConcept> implements FHIRGraphNode {

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
		String START = "start";
		String REFSETS = "refsets";
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

	@SuppressWarnings("unused")// Used in Elasticsearch queries, not code.
	@Field(type = FieldType.Object)
	private Map<String, Set<Object>> attr;

	@Field(type = FieldType.Keyword, index = false, store = true)
	// Format:
	// groupNo:attr=value:attr=value,value|groupNo:attr=value:attr=value,value
	private String attrMap;

	@Field(type = FieldType.Long)
	private Set<Long> refsets;

	@Transient
	private Map<Integer, Map<String, List<Object>>> groupedAttributesMap;

	@Transient
	@JsonIgnore
	private boolean creating;

	public QueryConcept() {
	}

	public QueryConcept(Long conceptId, Set<Long> parentIds, Set<Long> ancestorIds, boolean stated) {
		this.conceptIdL = conceptId;
		this.parents = parentIds;
		this.ancestors = ancestorIds;
		this.stated = stated;
		updateConceptIdForm();
	}

	public QueryConcept(QueryConcept queryConcept) {
		conceptIdForm = queryConcept.conceptIdForm;
		conceptIdL = queryConcept.conceptIdL;
		parents = new HashSet<>(queryConcept.parents);
		ancestors = new HashSet<>(queryConcept.ancestors);
		stated = queryConcept.stated;
		attrMap = queryConcept.attrMap;
		serializeGroupedAttributesMap();// Populates attr field
		refsets = queryConcept.refsets;
	}

	public void clearAttributes() {
		if (groupedAttributesMap != null) {
			groupedAttributesMap.clear();
		}
		attrMap = null;
		attr = null;
	}

	public void addAttribute(int group, Long type, Object value) {
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
		Map<String, List<Object>> groupAttributes = groupedAttributesMap.get(group);
		if (groupAttributes != null) {

			List<Object> typeValues = groupAttributes.get(type.toString());
			if (typeValues != null) {
				typeValues.remove(value);
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
	public Map<Integer, Map<String, List<Object>>> getGroupedAttributesMap() {
		if (groupedAttributesMap == null && this.attrMap != null) {
			return GroupedAttributesMapSerializer.deserializeMap(this.attrMap);
		}
		return groupedAttributesMap;
	}

	public Map<String, Set<Object>> getAttr() {
		return GroupedAttributesMapSerializer.serializeFlatMap(getGroupedAttributesMap());
	}

	public void setAttr(Map<String, Set<Object>> attr) {
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

	public boolean isRoot() {
		return Concepts.SNOMEDCT_ROOT.equals(conceptIdL.toString());
	}

	public Long getConceptIdL() {
		return conceptIdL;
	}

	@Override
	public String getCode() {
		return conceptIdL.toString();
	}

	@Override
	public String getCodeField() {
		return Fields.CONCEPT_ID;
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

	public boolean fieldsMatch(QueryConcept other) {
		if (!this.equals(other)
				|| !this.getParents().equals(other.getParents())
				|| !this.getAncestors().equals(other.getAncestors())
				|| !Objects.equals(this.getRefsets(), other.getRefsets())) {
			return false;
		}
		final Map<Integer, Map<String, List<Object>>> groupedAttributesMap = orEmpty(this.getGroupedAttributesMap());
		final Map<Integer, Map<String, List<Object>>> otherGroupedAttributesMap = orEmpty(other.getGroupedAttributesMap());
		// Sort both before comparing
		groupedAttributesMap.values().forEach(value -> value.values().forEach(list -> list.sort(null)));
		otherGroupedAttributesMap.values().forEach(value -> value.values().forEach(list -> list.sort(null)));
		return groupedAttributesMap.equals(otherGroupedAttributesMap);
	}

	private <K, V> Map<K, V> orEmpty(Map<K, V> map) {
		return map != null ? map : new HashMap<>();
	}

	public void setCreating(boolean creating) {
		this.creating = creating;
	}

	public boolean isCreating() {
		return creating;
	}

	public Set<Long> getRefsets() {
		return refsets;
	}

	public void setRefsets(Set<Long> refsets) {
		this.refsets = refsets;
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

		private static String serializeMap(Map<Integer, Map<String, List<Object>>> groupedAttributesMap) {
			if (groupedAttributesMap == null) {
				return "";
			}
			final StringBuilder builder = new StringBuilder();
			for (Integer groupNo : groupedAttributesMap.keySet()) {
				Map<String, List<Object>> attributes = groupedAttributesMap.get(groupNo);
				builder.append(groupNo);
				builder.append(":");
				for (String type : attributes.keySet()) {
					builder.append(type);
					builder.append("=");
					for (Object value : attributes.get(type)) {
						String valueString;
						if (value instanceof String) {
							valueString = (String) value;
						} else {
							builder.append("#");
							if (value instanceof Double || value instanceof Float) {
								// Maximum decimal places is 6 - See https://confluence.ihtsdotools.org/display/mag/Concrete+Domain+Decimal+Places+and+Rounding
								valueString = new DecimalFormat("#0.0#####",  new DecimalFormatSymbols(Locale.ROOT)).format(value);
							} else {
								valueString = value.toString();
							}
						}
						builder.append(valueString);
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

		private static Map<Integer, Map<String, List<Object>>> deserializeMap(String attrMap) {
			Map<Integer, Map<String, List<Object>>> groupedAttributesMap = new HashMap<>();
			if (attrMap.isEmpty()) {
				return groupedAttributesMap;
			}
			String[] groups = attrMap.split("\\|");
			for (String group : groups) {
				// To exclude : in concrete string value
				String[] attributes = group.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
				int groupNo = Integer.parseInt(attributes[0]);
				Map<String, List<Object>> attributeMap = new HashMap<>();
				for (int i = 1; i < attributes.length; i++) {
					String attribute = attributes[i];
					String[] attrParts = attribute.split("=");
					if (attrParts.length != 2) {
						throw new IllegalArgumentException(String.format("Invalid attribute format %s found in attrMap %s", attribute, attrMap));
					}
					String type = attrParts[0];
					String[] values = attrParts[1].split(",");
					List<Object> transformed = checkAndTransformConcreteValues(Arrays.asList(values));
					transformed.sort(null);
					attributeMap.put(type, transformed);
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
					// integer value
					int intValue = NumberUtils.toInt(numeric, -1);
					if (intValue != -1) {
						transformed.add(intValue);
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

		private static Map<String, Set<Object>> serializeFlatMap(Map<Integer, Map<String, List<Object>>> groupedAttributesMap) {
			Map<String, Set<Object>> attributesMap = new HashMap<>();
			Set<Object> allValues = new HashSet<>();
			Set<Object> allNumericValues = new HashSet<>();
			if (groupedAttributesMap != null) {
				groupedAttributesMap.forEach((group, attributes) -> attributes.forEach((type, values) -> {
					Set<Object> valueList = attributesMap.computeIfAbsent(type, (t) -> new HashSet<>());
					valueList.addAll(values);
					// add numeric concrete values to all numeric values field for wildcard query
					List<Object> numericValues = values.stream().filter(v -> !(v instanceof String)).toList();
					if (!numericValues.isEmpty()) {
						for (Object numericValue : numericValues) {
							// to make sure the all_numeric field is set to float data type
							allNumericValues.add(Float.valueOf(numericValue.toString()));
						}
					} else {
						allValues.addAll(values);
					}
				}));
			}
			attributesMap.put(ATTR_TYPE_WILDCARD, allValues);
			if (!allNumericValues.isEmpty()) {
				attributesMap.put(ATTR_NUMERIC_TYPE_WILDCARD, allNumericValues);
			}
			return attributesMap;
		}
	}
}
