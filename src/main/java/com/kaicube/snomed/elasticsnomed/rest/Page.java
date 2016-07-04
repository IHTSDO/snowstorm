package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.List;

public class Page<T> {

	private final org.springframework.data.domain.Page<T> page;

	public Page(org.springframework.data.domain.Page<T> page) {
		this.page = page;
	}

	@JsonView(View.Component.class)
	public List<T> getContent() {
		return page.getContent();
	}

	@JsonView(View.Component.class)
	public int getNumber() {
		return page.getNumber();
	}

	@JsonView(View.Component.class)
	public int getSize() {
		return page.getSize();
	}

	@JsonView(View.Component.class)
	public int getTotalPages() {
		return page.getTotalPages();
	}

	@JsonView(View.Component.class)
	public long getTotalElements() {
		return page.getTotalElements();
	}
}
