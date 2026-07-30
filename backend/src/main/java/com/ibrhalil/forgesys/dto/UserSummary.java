package com.ibrhalil.forgesys.dto;

import java.util.UUID;

/** Lightweight user reference (id + email) embedded in {@link GroupResponse#members()}. */
public record UserSummary(UUID id, String email) {
}
