package com.ibrhalil.forgesys.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging via {@link AuditLogAspect}.
 * The aspect resolves SpEL expressions against the method's arguments and return value,
 * then delegates to {@link com.ibrhalil.forgesys.service.AuditService} in a
 * {@code REQUIRES_NEW} transaction (best-effort, exceptions swallowed).
 *
 * <p>Usage example:
 * <pre>{@code
 * @AuditLog(
 *     action = "user_created",
 *     entityType = "User",
 *     entityId = "#result.id",
 *     entityName = "#result.email"
 * )
 * public UserResponse create(UserCreateRequest request) { ... }
 * }</pre>
 *
 * For privilege changes (role/group/permission assignments) use {@code captureDelta=true}
 * with {@code oldValue}/{@code newValue} SpEL expressions that evaluate to the before/after
 * name collections (e.g. "{@code #beforeRoleNames}", "{@code #afterRoleNames}").
 * The caller must ensure these are available in the evaluation context (see
 * {@link AuditLogAspect} for the variables it exposes).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** Stable action key, e.g. "user_created", "role_permissions_updated". */
    String action();

    /** Entity class label, e.g. "User", "Role", "App". */
    String entityType();

    /**
     * SpEL expression for the affected entity's id.
     * Available variables: method parameters by name, {@code #result} (return value),
     * {@code #beforeX}/{@code #afterX} for delta capture (see {@link #captureDelta()}).
     * Empty string means {@code null} entity id.
     */
    String entityId() default "";

    /**
     * SpEL expression for the human-readable entity label (email, name).
     * Same variables as {@link #entityId()}.
     * Empty string means {@code null} entity name.
     */
    String entityName() default "";

    /**
     * If {@code true}, the aspect calls {@link com.ibrhalil.forgesys.service.AuditService#recordDelta}
     * with {@link #oldValue()} and {@link #newValue()} SpEL expressions.
     * Default {@code false} calls {@link com.ibrhalil.forgesys.service.AuditService#record}.
     */
    boolean captureDelta() default false;

    /**
     * SpEL expression for the old value (JSON array of names).
     * Only used when {@link #captureDelta()} is {@code true}.
     * Example: "{@code #beforeRoleNames}".
     */
    String oldValue() default "";

    /**
     * SpEL expression for the new value (JSON array of names).
     * Only used when {@link #captureDelta()} is {@code true}.
     * Example: "{@code #afterRoleNames}".
     */
    String newValue() default "";
}