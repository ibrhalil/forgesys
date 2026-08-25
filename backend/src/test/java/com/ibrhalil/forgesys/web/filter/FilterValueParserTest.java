package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterValueParserTest {

    @Test
    void parsesDateField() {
        FilterFieldSet.RegisteredField field = field(FilterFieldType.DATE);
        assertThat(FilterValueParser.parse(field, "2026-01-15")).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(FilterValueParser.parse(field, " 2026-01-15 ")).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void rejectsInvalidDate() {
        assertThatThrownBy(() -> FilterValueParser.parse(field(FilterFieldType.DATE), "15.01.2026"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void parsesIntField() {
        FilterFieldSet.RegisteredField field = field(FilterFieldType.INT);
        assertThat(FilterValueParser.parse(field, "404")).isEqualTo(404);
        assertThat(FilterValueParser.parse(field, " -1 ")).isEqualTo(-1);
    }

    @Test
    void rejectsInvalidInt() {
        assertThatThrownBy(() -> FilterValueParser.parse(field(FilterFieldType.INT), "HTTP 404"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private static FilterFieldSet.RegisteredField field(FilterFieldType type) {
        return FilterFieldSet.builder().field("probe", type, false).build().get("probe");
    }
}
