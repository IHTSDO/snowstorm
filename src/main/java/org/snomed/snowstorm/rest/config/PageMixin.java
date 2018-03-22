package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public abstract class PageMixin {

	@JsonView(value = View.Component.class)
	abstract int getTotalPages();

	@JsonView(value = View.Component.class)
	abstract long getTotalElements();

	@JsonView(value = View.Component.class)
	abstract int getSize();

	@JsonView(value = View.Component.class)
	abstract int getNumber();

	@JsonView(value = View.Component.class)
	abstract int getNumberOfElements();

	@JsonView(value = View.Component.class)
	abstract boolean isFirst();

	@JsonView(value = View.Component.class)
	abstract boolean isLast();

	@JsonProperty("items")
	@JsonView(value = View.Component.class)
	abstract List getContent();

	@JsonIgnore
	abstract Sort getSort();

	@JsonIgnore
	abstract Pageable getPageable();

}
