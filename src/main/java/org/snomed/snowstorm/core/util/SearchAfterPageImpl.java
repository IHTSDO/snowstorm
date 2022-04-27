package org.snomed.snowstorm.core.util;

import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class SearchAfterPageImpl<T> extends PageImpl<T> implements SearchAfterPage<T> {
	private static final long serialVersionUID = -281372840330069871L;

	private final String searchAfter;

	public SearchAfterPageImpl(List<T> content, Pageable pageable, long total, Object[] searchAfterArray) {
		super(content, pageable, total);
		this.searchAfter = SearchAfterHelper.toSearchAfterToken(searchAfterArray);
	}

	@Override
	public Object[] getSearchAfter() {
		return  SearchAfterHelper.fromSearchAfterToken(this.searchAfter);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof SearchAfterPageImpl)) return false;
		if (!super.equals(o)) return false;

		SearchAfterPageImpl<?> that = (SearchAfterPageImpl<?>) o;
		return SearchAfterHelper.toSearchAfterToken(getSearchAfter()).equals(SearchAfterHelper.toSearchAfterToken(that.getSearchAfter()));
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + SearchAfterHelper.toSearchAfterToken(getSearchAfter()).hashCode();
		return result;
	}
}
