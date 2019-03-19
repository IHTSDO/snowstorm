package org.snomed.snowstorm.loadtest;

import java.util.List;

class ItemsPagePojo<T> {

	private List<T> items;
	private long total;
	private long limit;
	private Long offset;
	private String searchAfterToken;

	public ItemsPagePojo() {
	}

	public List<T> getItems() {
		return items;
	}

	public void setItems(List<T> items) {
		this.items = items;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	public Long getOffset() {
		return offset;
	}

	public void setOffset(Long offset) {
		this.offset = offset;
	}

	public String getSearchAfterToken() {
		return searchAfterToken;
	}

	public void setSearchAfterToken(String searchAfterToken) {
		this.searchAfterToken = searchAfterToken;
	}
}
