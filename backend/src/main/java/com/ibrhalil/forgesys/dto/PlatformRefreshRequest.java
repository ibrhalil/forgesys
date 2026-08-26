package com.ibrhalil.forgesys.dto;

/** K-50 platform refresh — body takes precedence over the {@code sf_platform_refresh_token} cookie. */
public record PlatformRefreshRequest(String refreshToken) {
}
