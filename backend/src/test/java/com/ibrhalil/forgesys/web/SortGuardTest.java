package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortGuardTest {

    private static final FilterFieldSet FIELDS = FilterFieldSet.builder()
            .field("name", FilterFieldType.STRING, true)
            .joinedField("projectName", FilterFieldType.STRING, false, "project", "name")
            .subqueryField("noteCount", FilterFieldType.NUMERIC, false,
                    (root, query, cb) -> query.subquery(Long.class))
            .membershipField("memberIds", "members", "id")
            .build();

    @Test
    void allowsSortableJoinedAndSubqueryFields() {
        assertThatCode(() -> SortGuard.require(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "projectName").and(Sort.by("noteCount"))),
                FIELDS))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonSortableMembershipField() {
        assertThatThrownBy(() -> SortGuard.require(PageRequest.of(0, 10, Sort.by("memberIds")), FIELDS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void rejectsUnknownFieldListingSortableNames() {
        assertThatThrownBy(() -> SortGuard.require(PageRequest.of(0, 10, Sort.by("password")), FIELDS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("noteCount")
                .hasMessageContaining("projectName");
    }
}
