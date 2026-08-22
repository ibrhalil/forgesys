package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.RequestLog;
import com.ibrhalil.forgesys.persistence.repository.RequestLogRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    @Mock
    private ObjectProvider<RequestLogService> self;

    @InjectMocks
    private RequestLogService service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        CustomUserDetails principal = new CustomUserDetails(
                userId, "admin@example.com", "pw", true, true, true, true, java.util.Set.of(), "tenant_test", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.Set.of()));
        RequestContext.set(new RequestMeta("trace-1", "10.0.0.1", "UA"));
        lenient().when(self.getObject()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void recordPopulatesActorAndRequestContextAndSaves() {
        String traceId = "trace-1";
        String method = "POST";
        String path = "/api/v1/users";
        int status = 201;
        long durationMs = 42;
        String requestBody = "{\"password\":\"secret\"}";

        service.record(traceId, method, path, status, durationMs, null, null, null, null, requestBody);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());
        RequestLog saved = captor.getValue();
        assertEquals(traceId, saved.getTraceId());
        assertEquals(method, saved.getMethod());
        assertEquals(path, saved.getPath());
        assertEquals(status, saved.getStatus());
        assertEquals(durationMs, saved.getDurationMs());
        assertEquals(userId, saved.getUserId());
        assertEquals("admin@example.com", saved.getUsername());
        assertEquals("10.0.0.1", saved.getIpAddress());
        assertEquals("UA", saved.getUserAgent());
        assertEquals(requestBody, saved.getRequestBody());
    }

    @Test
    void recordUsesFallbackWhenNoPrincipal() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();

        service.record("trace-2", "GET", "/api/v1/users", 200, 10L, null, null, null, null, null);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());
        RequestLog saved = captor.getValue();
        assertEquals("trace-2", saved.getTraceId());
        assertEquals("GET", saved.getMethod());
        assertEquals("/api/v1/users", saved.getPath());
        assertEquals(200, saved.getStatus());
        assertEquals(10L, saved.getDurationMs());
    }

    @Test
    void recordSwallowsRepositoryFailureAndNeverRethrows() {
        when(requestLogRepository.save(any(RequestLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record("trace-3", "POST", "/api/v1/users", 500, 5L, null, null, null, null, null));
        verify(requestLogRepository).save(any(RequestLog.class));
    }
}