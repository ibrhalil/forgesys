package com.ibrhalil.forgesys.security.refresh;

/** Freshly issued refresh token (raw, client-bound; URL-safe Base64, 32 bytes entropy) + session metadata; only the hash is stored. */
public record IssuedRefresh(String token, RefreshSession session) {
}
