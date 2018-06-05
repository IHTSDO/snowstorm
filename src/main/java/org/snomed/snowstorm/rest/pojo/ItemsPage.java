package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.domain.Page;

import java.util.Collection;

public class ItemsPage<T> {

	private final Collection<T> items;
	private final long total;
	private final long limit;
	private final long offset;

	public ItemsPage(Collection<T> items) {
		this.items = items;
		this.limit = items.size();
		this.total = items.size();
		this.offset = 0;
	}

	public ItemsPage(Collection<T> items, long total) {
		this.items = items;
		this.limit = items.size();
		this.total = total;
		this.offset = 0;
	}

	public ItemsPage(Page<T> page) {
		this.items = page.getContent();
		this.limit = page.getSize();
		this.total = page.getTotalElements();
		this.offset = page.getNumber() * page.getSize();
	}

	@JsonView(View.Component.class)
	public Collection<T> getItems() {
		return items;
	}

	@JsonView(View.Component.class)
	public long getTotal() {
		return total;
	}

	@JsonView(View.Component.class)
	public long getLimit() {
		return limit;
	}

	@JsonView(View.Component.class)
	public long getOffset() {
		return offset;
	}
}
