package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterFieldSetTest {

    private static final FilterFieldSet FIELDS = FilterFieldSet.builder()
            .field("email", FilterFieldType.STRING, true)
            .field("firstName", FilterFieldType.STRING, true)
            .field("enabled", FilterFieldType.BOOLEAN, false)
            .subqueryField("roleCount", FilterFieldType.NUMERIC, false,
                    (root, query, cb) -> query.subquery(Long.class))
            .membershipField("roleIds", "roles", "id")
            .build();

    @Test
    void membershipFieldsAreFilterOnly() {
        FilterFieldSet.RegisteredField roleIds = FIELDS.get("roleIds");
        assertThat(roleIds.sortable()).isFalse();
        assertThat(roleIds.searchable()).isFalse();
        assertThat(roleIds.supports(FilterOperator.IN)).isTrue();
        assertThat(roleIds.supports(FilterOperator.NOT_IN)).isTrue();
        assertThat(roleIds.supports(FilterOperator.IS_NULL)).isTrue();
        assertThat(roleIds.supports(FilterOperator.IS_NOT_NULL)).isTrue();
        assertThat(roleIds.supports(FilterOperator.EQ)).isFalse();
        assertThat(FIELDS.sortableNames()).containsExactly("email", "enabled", "firstName", "roleCount");
    }

    @Test
    void rejectsNonStringSearchableField() {
        assertThatThrownBy(() -> FilterFieldSet.builder().field("active", FilterFieldType.BOOLEAN, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void qFieldsSelectASearchableSubset() {
        assertThatCode(() -> FilterSpecifications.from(FIELDS, "ali", List.of("firstName"), List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> FilterSpecifications.from(FIELDS, "ali", null, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownQFieldFailsWithValidation() {
        assertThatThrownBy(() -> FilterSpecifications.from(FIELDS, "ali", List.of("nope"), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void nonSearchableQFieldFailsWithValidation() {
        assertThatThrownBy(() -> FilterSpecifications.from(FIELDS, "ali", List.of("enabled"), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
