package com.ibrhalil.forgesys.dto;

/**
 * Response of {@code POST /api/v1/auth/verify-email}.
 */
public record EmailVerificationResponse(String message) {
}
