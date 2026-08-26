package com.ibrhalil.forgesys.web;

import java.util.Optional;

/**
 * ThreadLocal holder for the masked request body; cleared only by
 * {@code RequestLogFilter}'s finally (the single clear point).
 */
public final class AuditRequestContext {

    private static final ThreadLocal<String> requestBody = new ThreadLocal<>();

    private AuditRequestContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void setRequestBody(String body) {
        requestBody.set(body);
    }

    public static Optional<String> getRequestBody() {
        return Optional.ofNullable(requestBody.get());
    }

    public static String getAndClearRequestBody() {
        String body = requestBody.get();
        requestBody.remove();
        return body;
    }

    public static void clear() {
        requestBody.remove();
    }
}