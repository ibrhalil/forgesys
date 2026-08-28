package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.SearchRequest;

/**
 * Resolved {@code ?sq=} search query (K-55): the decoded {@link SearchRequest} when
 * the param is present, {@link #empty()} otherwise. GET list endpoints branch on
 * {@link #present()}; when present, flat query params (including scoped ones) are
 * ignored — documented precedence. Bound by {@link SearchQueryArgumentResolver}.
 */
public record SearchQuery(SearchRequest request) {

    private static final SearchQuery EMPTY = new SearchQuery(null);

    public static SearchQuery empty() {
        return EMPTY;
    }

    public static SearchQuery of(SearchRequest request) {
        return new SearchQuery(request);
    }

    public boolean present() {
        return request != null;
    }
}
