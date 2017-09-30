package org.ihtsdo.elasticsnomed.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;
import org.springframework.data.domain.Page;

import java.util.Collection;

public class ItemsPage<T> {

	private final Collection<T> items;
	private final long total;

	public ItemsPage(Collection<T> items) {
		this.items = items;
		total = items.size();
	}

	public ItemsPage(Collection<T> items, long total) {
		this.items = items;
		this.total = total;
	}

	public ItemsPage(Page<T> page) {
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
