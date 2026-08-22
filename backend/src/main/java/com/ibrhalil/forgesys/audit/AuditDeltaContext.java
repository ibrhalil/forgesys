package com.ibrhalil.forgesys.audit;

import java.util.Optional;

/**
 * ThreadLocal holder for audit delta values (before/after name collections)
 * set by service methods annotated with {@link AuditLog#captureDelta()}.
 * The aspect reads these after the method proceeds and clears them.
 */
public final class AuditDeltaContext {

    private static final ThreadLocal<String> oldValue = new ThreadLocal<>();
    private static final ThreadLocal<String> newValue = new ThreadLocal<>();

    private AuditDeltaContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void setOldValue(String value) {
        oldValue.set(value);
    }

    public static void setNewValue(String value) {
        newValue.set(value);
    }

    public static Optional<String> getOldValue() {
        return Optional.ofNullable(oldValue.get());
    }

    public static Optional<String> getNewValue() {
        return Optional.ofNullable(newValue.get());
    }

    public static void clear() {
        oldValue.remove();
        newValue.remove();
    }
}