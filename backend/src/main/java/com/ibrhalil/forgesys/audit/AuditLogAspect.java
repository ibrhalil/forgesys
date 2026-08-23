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
            // point for AuditRequestContext — consuming here would leave the request-log
            // row without its body, and the value must survive until that finally runs.
            String requestBody = AuditRequestContext.getRequestBody().orElse(null);

            // Notify test hook if registered (for unit tests without Spring context)
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

    private void updateLatestAuditLogRequestBody(String requestBody) {
        // Note: This is a best-effort update. The audit log was already saved in REQUIRES_NEW.
        // We cannot easily update it without another REQUIRES_NEW transaction and finding the
        // exact log entry (by traceId + actor + action + entity). For now, request body capture
        // is primarily handled by RequestLogFilter for request logs. Audit logs get request body
        // only when the aspect runs within the same request context where RequestBodyCaptureFilter
        // already stored it. Since AuditService.record runs in REQUIRES_NEW, the request body
        // would need to be passed at record time. This is a known limitation for now.
        // The main request body capture is in RequestLog via RequestLogFilter.
    }
}