package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.SortCriteria;
import com.ibrhalil.forgesys.web.SortGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Maps a {@link SearchRequest} onto a {@link Pageable}, running the sort through
 * {@link SortGuard} — POST /search bodies get the same sort whitelist as the GET
 * lists, so no property-path injection path exists on either surface.
 */
public final class SearchRequests {

    /** Default page size for search bodies without one — matches the GET default. */
    private static final int DEFAULT_SIZE = 20;

    private SearchRequests() {
    }

    public static Pageable toPageable(SearchRequest request, FilterFieldSet fields) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        Sort sort = Sort.unsorted();
        if (request.sorts() != null) {
            for (SortCriteria s : request.sorts()) {
                sort = sort.and(Sort.by(Sort.Direction.fromString(s.direction()), s.field()));
            }
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        SortGuard.require(pageable, fields);
        return pageable;
    }
}
