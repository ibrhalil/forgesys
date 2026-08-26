package com.ibrhalil.forgesys.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * ThreadLocal holder for the current request's {@link RequestMeta}; populated by
 * {@link RequestMetadataFilter} and cleared in its {@code finally}.
 * rationale: docs/CODE_NOTES.md (backend/web → RequestContext)
 */
public final class RequestContext {

    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);
    private static final ThreadLocal<RequestMeta> current = new ThreadLocal<>();

    private RequestContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void set(RequestMeta meta) {
        // Only the trace id is logged — client IP / User-Agent are PII.
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
