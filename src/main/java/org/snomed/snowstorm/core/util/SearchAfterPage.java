package org.snomed.snowstorm.core.util;

import org.springframework.data.domain.Page;

public interface SearchAfterPage<T> extends Page<T> {

	Object[] getSearchAfter();

}