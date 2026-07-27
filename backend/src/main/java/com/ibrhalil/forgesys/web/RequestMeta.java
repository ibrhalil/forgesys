package com.ibrhalil.forgesys.web;

/**
 * Immutable per-request metadata captured by {@link RequestMetadataFilter} and
 * exposed to the service layer via {@link RequestContext}. Lets services record
 * the client IP, User-Agent and trace id (audit log / login history) without
 * touching the servlet API.
 *
 * @param traceId   stable per-request trace id (also mirrored into the SLF4J MDC)
 * @param clientIp  resolved client IP (X-Forwarded-For / X-Real-IP / remote addr)
 * @param userAgent User-Agent header, truncated to the DB column limit
 */
public record RequestMeta(String traceId, String clientIp, String userAgent) {
}
