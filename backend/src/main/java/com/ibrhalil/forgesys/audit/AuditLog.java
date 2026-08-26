package com.ibrhalil.forgesys.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging via {@link AuditLogAspect}: SpEL
 * expressions resolve against the method's arguments and {@code #result}; the
 * write is REQUIRES_NEW and best-effort (exceptions swallowed).
 * rationale: docs/CODE_NOTES.md (backend/web → AuditLog)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** Stable action key, e.g. "user_created", "role_permissions_updated". */
    String action();

    /** Entity class label, e.g. "User", "Role", "App". */
    String entityType();

    /** SpEL for the entity id — variables: params by name, {@code #result}. Empty = null. */
    String entityId() default "";

    /** SpEL for the entity label (email/name); same variables as {@link #entityId()}. */
    String entityName() default "";

    /** When true, calls {@code AuditService.recordDelta} with {@link #oldValue()}/{@link #newValue()}. */
    boolean captureDelta() default false;

    /** SpEL for the before-collection (JSON names); delta mode only. */
    String oldValue() default "";

    /** SpEL for the after-collection (JSON names); delta mode only. */
    String newValue() default "";
}