package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.SearchRequest;

/**
 * Resolved {@code ?sq=} search query (K-55): the decoded {@link SearchRequest}
 * when the param is present, {@link #empty()} otherwise. The blob carries the
 * FILTER part only (q/qFields/filters) — paging and sorting travel as ordinary
 * flat params resolved by Spring's {@link Pageable} machinery, so the endpoint's
 * single {@code SortGuard.require} covers both paths. When present, the blob's
 * q/filters take precedence over the flat {@code q} and scoped filter params
 * (documented precedence). Bound by {@link SearchQueryArgumentResolver}.
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
