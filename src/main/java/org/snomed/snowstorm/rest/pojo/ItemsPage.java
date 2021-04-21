package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.rest.View;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;

import java.util.Collection;

public class ItemsPage<T> {

	private final Collection<T> items;
	private final long total;
	private final long limit;
	private final Long offset;
	private final String searchAfter;
	private final Object[] searchAfterArray;

	//Default constructor for Jackson.
	public ItemsPage() {
		this.items = null;
		this.total = -1;
		this.limit = -1;
		this.offset = -1L;
		this.searchAfter = null;
		this.searchAfterArray = null;
	}

	public ItemsPage(Collection<T> items) {
		this.items = items;
		this.limit = items.size();
		this.total = items.size();
		this.offset = 0L;
		this.searchAfter = null;
		this.searchAfterArray = null;
	}

	public ItemsPage(Collection<T> items, long total) {
		this.items = items;
		this.limit = items.size();
		this.total = total;
		this.offset = 0L;
		this.searchAfter = null;
		this.searchAfterArray = null;
	}

	public ItemsPage(Page<T> page) {
		this.items = page.getContent();
		this.limit = page.getSize();
		this.total = page.getTotalElements();
		boolean searchAfterRequested = false;
		if (page instanceof SearchAfterPage) {
			SearchAfterPage searchAfterPage = (SearchAfterPage) page;
			Object[] searchAfterArray = searchAfterPage.getSearchAfter();
			this.searchAfter = SearchAfterHelper.toSearchAfterToken(searchAfterArray);
			this.searchAfterArray = searchAfterArray;
			if (searchAfterPage.getPageable() instanceof SearchAfterPageRequest) {
				searchAfterRequested = true;
			}
		} else {
			this.searchAfter = null;
			this.searchAfterArray = null;
		}
		if (searchAfterRequested) {
			offset = null;
		} else {
			this.offset = page.getNumber() * (long) page.getSize();
		}
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
	public Long getOffset() {
		return offset;
	}

	@JsonView(View.Component.class)
	public String getSearchAfter() {
		return searchAfter;
	}

	@JsonView(View.Component.class)
	public Object[] getSearchAfterArray() {
		return searchAfterArray;
	}
}
