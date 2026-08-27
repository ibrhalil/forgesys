package com.ibrhalil.forgesys.dto;

/** K-51 mail-test template catalog entry. {@code name} is the wire value for preview/test-send requests. */
public record PlatformMailTemplateInfo(
        String name,
        String key,
        String subjectTr,
        String subjectEn) {
}
