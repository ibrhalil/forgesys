package com.ibrhalil.forgesys.dto;

import java.util.UUID;

public record NoteCategoryResponse(
        UUID id,
        String name,
        String color
) {
}
