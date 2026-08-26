package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.Set;

/**
 * Sort whitelist for paged list endpoints — every list MUST pass its
 * {@link Pageable} through {@link #require}: a resolvable nested property path
 * (e.g. {@code userAccount.tokenInvalidBefore}) bypasses the unknown-property
 * guard and leaks internal model shape. Violation → 400 {@code validation_error}.
 * rationale: docs/CODE_NOTES.md (backend/web → SortGuard)
 */
public final class SortGuard {

    private SortGuard() {
    }

    public static void require(Pageable pageable, String... allowedFields) {
        if (!pageable.getSort().isSorted()) {
            return;
        }
        Set<String> allowed = Set.of(allowedFields);
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw unsupported(order.getProperty(), allowed);
            }
        }
    }

    public static void require(Pageable pageable, FilterFieldSet fields) {
        if (!pageable.getSort().isSorted()) {
            return;
        }
        for (Sort.Order order : pageable.getSort()) {
            FilterFieldSet.RegisteredField field = fields.get(order.getProperty());
            if (field == null || !field.sortable()) {
                throw unsupported(order.getProperty(), fields.sortableNames());
            }
        }
    }

    private static BusinessException unsupported(String property, Collection<String> allowed) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Unsupported sort property: '" + property + "'. Allowed: "
                        + allowed.stream().sorted().toList());
    }
}
