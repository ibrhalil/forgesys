package com.ibrhalil.forgesys.security.refresh;

/**
 * A freshly issued refresh token plus its session metadata (K-34). The raw opaque
 * token is handed to the client (cookie/body); the store keeps only its SHA-256 hash.
 *
 * @param token   raw opaque refresh token (URL-safe Base64, 32 bytes of entropy)
 * @param session bound session metadata
 */
public record IssuedRefresh(String token, RefreshSession session) {
}
