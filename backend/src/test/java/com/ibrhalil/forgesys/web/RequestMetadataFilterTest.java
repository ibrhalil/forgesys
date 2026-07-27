package com.ibrhalil.forgesys.web;

import com.ibrhalil.forgesys.exception.ApiErrorFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestMetadataFilterTest {

    private final RequestMetadataFilter filter = new RequestMetadataFilter();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContext.clear();
    }

    @Test
    void resolveTraceIdHonorsValidRequestIdHeader() {
        when(request.getHeader("X-Request-Id")).thenReturn("abc-123_456");

        assertEquals("abc-123_456", filter.resolveTraceId(request));
    }

    @Test
    void resolveTraceIdGeneratesUuidWhenHeaderMissing() {
        String traceId = filter.resolveTraceId(request);

        assertNotNull(traceId);
        assertTrue(traceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                "expected a generated UUID, got: " + traceId);
    }

    @Test
    void resolveTraceIdRejectsMalformedHeaderAndGeneratesUuid() {
        when(request.getHeader("X-Request-Id")).thenReturn("bad trace id with spaces!");

        String traceId = filter.resolveTraceId(request);

        assertNotNull(traceId);
        assertTrue(traceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                "malformed header must fall back to a generated UUID, got: " + traceId);
    }

    @Test
    void resolveClientIpPrefersFirstForwardedForHop() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 70.41.0.1, 10.0.0.1");

        assertEquals("203.0.113.5", filter.resolveClientIp(request));
    }

    @Test
    void resolveClientIpFallsBackToRealIpHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.7");

        assertEquals("198.51.100.7", filter.resolveClientIp(request));
    }

    @Test
    void resolveClientIpFallsBackToRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", filter.resolveClientIp(request));
    }

    @Test
    void resolveUserAgentTruncatesToLimit() {
        when(request.getHeader("User-Agent")).thenReturn("x".repeat(600));

        assertEquals(500, filter.resolveUserAgent(request).length());
    }

    @Test
    void resolveUserAgentReturnsNullWhenAbsent() {
        assertNull(filter.resolveUserAgent(request));
    }

    @Test
    void doFilterPopulatesMdcAndContextAndClearsAfter() throws Exception {
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getHeader("X-Request-Id")).thenReturn("req-xyz");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        doAnswer(invocation -> {
            assertEquals("req-xyz", MDC.get(ApiErrorFactory.TRACE_ID_KEY));
            assertTrue(RequestContext.current().isPresent());
            RequestMeta meta = RequestContext.current().get();
            assertEquals("req-xyz", meta.traceId());
            assertEquals("10.0.0.9", meta.clientIp());
            assertEquals("TestAgent", meta.userAgent());
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get(ApiErrorFactory.TRACE_ID_KEY));
        assertTrue(RequestContext.current().isEmpty());
    }

    @Test
    void doFilterClearsMdcAndContextEvenWhenChainThrows() throws Exception {
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        doThrow(new RuntimeException("boom")).when(chain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilterInternal(request, response, chain));

        assertNull(MDC.get(ApiErrorFactory.TRACE_ID_KEY));
        assertTrue(RequestContext.current().isEmpty());
    }
}
