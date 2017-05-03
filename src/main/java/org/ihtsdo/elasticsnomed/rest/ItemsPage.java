package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.Collection;

public class ItemsPage<T> {

	private final Collection<T> items;

	public ItemsPage(Collection<T> items) {
		this.items = items;
	}

	@JsonView(View.Component.class)
	public Collection<T> getItems() {
		return items;
	}

	@JsonView(View.Component.class)
	public int getTotal() {
		return items.size();
	}
}
