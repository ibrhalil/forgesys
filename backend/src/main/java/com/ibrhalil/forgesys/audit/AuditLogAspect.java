package com.ibrhalil.forgesys.audit;

import com.ibrhalil.forgesys.service.AuditService;
import com.ibrhalil.forgesys.web.AuditRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AOP aspect behind {@link AuditLog}: resolves the SpEL expressions against the
 * method's args/return value and delegates to {@link AuditService} — best-effort,
 * never breaks the business op.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditService auditService;
    private final ObjectProvider<AuditService> self;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /** Test hook to capture audit calls in unit tests without Spring context. */
    private static volatile Consumer<AuditCapture> testHook;

    public static void setTestHook(Consumer<AuditCapture> hook) {
        testHook = hook;
    }

    public static void clearTestHook() {
        testHook = null;
    }

    public record AuditCapture(String action, String entityType, UUID entityId, String entityName,
                               String oldValue, String newValue, String requestBody) {}

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();

        Object result = pjp.proceed();

        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("result", result);
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }

            String entityId = evalSpel(auditLog.entityId(), ctx, UUID.class);
            String entityName = evalSpel(auditLog.entityName(), ctx, String.class);

            String oldValue = null;
            String newValue = null;
            if (auditLog.captureDelta()) {
                oldValue = AuditDeltaContext.getOldValue().orElse(null);
                newValue = AuditDeltaContext.getNewValue().orElse(null);
                AuditDeltaContext.clear();
                self.getObject().recordDelta(auditLog.action(), auditLog.entityType(),
                        parseUuid(entityId), entityName, oldValue, newValue);
            } else {
                self.getObject().record(auditLog.action(), auditLog.entityType(),
                        parseUuid(entityId), entityName);
            }

            // Peek (not get-and-clear): RequestLogFilter's finally is the single clear
            // point — consuming here would strip the request-log row of its body.
            String requestBody = AuditRequestContext.getRequestBody().orElse(null);

            Consumer<AuditCapture> hook = testHook;
            if (hook != null) {
                hook.accept(new AuditCapture(auditLog.action(), auditLog.entityType(),
                        parseUuid(entityId), entityName, oldValue, newValue, requestBody));
            }

        } catch (RuntimeException ex) {
            log.warn("Audit logging failed for {}#{}: {}", method.getDeclaringClass().getSimpleName(),
                    method.getName(), ex.toString());
        }

        return result;
    }

    private <T> String evalSpel(String expression, EvaluationContext ctx, Class<T> targetType) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            T value = spelParser.parseExpression(expression).getValue(ctx, targetType);
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            log.debug("SpEL evaluation failed for '{}': {}", expression, ex.toString());
            return null;
        }
    }

    private UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}