package org.snomed.snowstorm.core.util;

import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.Arrays;

public class SearchAfterQueryHelper {

    public static NativeQuery updateQueryWithSearchAfter(NativeQuery nativeQuery, PageRequest pageRequest) {
        if (pageRequest instanceof SearchAfterPageRequest searchAfterPageRequest) {
            nativeQuery.setSearchAfter(Arrays.stream(searchAfterPageRequest.getSearchAfter()).toList());
        }
        return nativeQuery;
    }
}
