package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.data.domain.Page;

import java.util.Collection;

public class ItemsPage<T> {

	private final Collection<T> items;
	private final long total;

	ItemsPage(Collection<T> items) {
		this.items = items;
		total = items.size();
	}

	ItemsPage(Page<T> page) {
		items = page.getContent();
		total = page.getTotalElements();
	}

	@JsonView(View.Component.class)
	public Collection<T> getItems() {
		return items;
	}

	@JsonView(View.Component.class)
	public long getTotal() {
		return total;
	}
}
