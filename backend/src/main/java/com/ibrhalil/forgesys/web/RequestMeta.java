package com.ibrhalil.forgesys.web;

/**
 * Immutable per-request metadata (trace id, client IP, User-Agent) captured by
 * {@link RequestMetadataFilter} — lets services record audit context without the
 * servlet API.
 */
public record RequestMeta(String traceId, String clientIp, String userAgent) {
}
