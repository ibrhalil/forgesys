package com.ibrhalil.forgesys.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * ThreadLocal holder for the current request's {@link RequestMeta}. Populated by
 * {@link RequestMetadataFilter} (runs before the tenant filter and the security
 * chain) and cleared in a {@code finally} block to avoid leaks across reused
 * request threads.
 *
 * <p>Mirrors the {@code common.tenant.TenantContext} pattern but lives in the
 * backend module: only the web layer writes it and only backend services read
 * it (the persistence layer has no use for request metadata). See the common
 * module rule &mdash; a type belongs in {@code common} only when shared by more
 * than one module.
 */
public final class RequestContext {

    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);
    private static final ThreadLocal<RequestMeta> current = new ThreadLocal<>();

    private RequestContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void set(RequestMeta meta) {
        // Log only the trace id &mdash; client IP / User-Agent are PII and not logged.
        log.debug("Setting request context (traceId={})", meta.traceId());
        current.set(meta);
    }

    public static Optional<RequestMeta> current() {
        return Optional.ofNullable(current.get());
    }

    public static void clear() {
        log.debug("Clearing request context");
        current.remove();
    }
}
