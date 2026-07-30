package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.persistence.repository.AuditLogRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.web.RequestContext;
import com.ibrhalil.forgesys.web.RequestMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService service;

    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        CustomUserDetails principal = new CustomUserDetails(
                actorId, "admin@example.com", "pw", true, true, true, true, Set.of(), "tenant_test", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void recordPopulatesActorAndRequestContextAndSaves() {
        RequestContext.set(new RequestMeta("trace-9", "10.0.0.1", "UA"));
        UUID entityId = UUID.randomUUID();

        service.record("user_created", "User", entityId, "new@example.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("user_created", saved.getAction());
        assertEquals("User", saved.getEntityType());
        assertEquals(entityId, saved.getEntityId());
        assertEquals("new@example.com", saved.getEntityName());
        assertEquals(actorId, saved.getActorId());
        assertEquals("admin@example.com", saved.getActorName());
        assertEquals("10.0.0.1", saved.getIpAddress());
        assertEquals("trace-9", saved.getTraceId());
    }

    @Test
    void recordUsesSystemActorWhenNoPrincipal() {
        SecurityContextHolder.clearContext();

        service.record("tenant_provisioned", "Tenant", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertNull(saved.getActorId());
        assertEquals(AuditService.SYSTEM_ACTOR, saved.getActorName());
    }

    @Test
    void recordLeavesIpAndTraceIdNullWhenNoRequestContext() {
        service.record("user_updated", "User", UUID.randomUUID(), "x@example.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertNull(saved.getIpAddress());
        assertNull(saved.getTraceId());
    }

    @Test
    void recordSwallowsRepositoryFailureAndNeverRethrows() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record("user_deleted", "User", UUID.randomUUID(), null));
        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
