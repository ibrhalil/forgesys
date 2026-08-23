package com.ibrhalil.forgesys.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps mutating requests on high-risk paths with a cached body and publishes the
 * masked body to {@link AuditRequestContext} BEFORE delegating down the chain, so
 * both {@code AuditLogAspect} (during the request) and {@link RequestLogFilter}
 * (in its finally, the single clear point) can consume it. Registered inside
 * {@link RequestLogFilter} (order -94, see
 * {@code SecurityConfig#requestBodyCaptureFilterRegistration}).
 */
@Slf4j
@Component
public class RequestBodyCaptureFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Set<String> maskPatterns;
    private final List<String> highRiskPaths;

    public RequestBodyCaptureFilter(
            @Value("${forgesys.audit.mask-patterns:password,token,secret,credential,authorization,apiKey,accessKey,clientSecret}") List<String> maskPatterns,
            @Value("${forgesys.audit.high-risk-paths:/api/v1/users/**,/api/v1/roles/**,/api/v1/groups/**,/api/v1/permissions/**,/api/v1/platform/**,/api/v1/modules/**,/api/v1/apps/**}") List<String> highRiskPaths) {
        this.maskPatterns = maskPatterns.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        this.highRiskPaths = highRiskPaths != null ? highRiskPaths : Collections.emptyList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only capture body for high-risk paths
        String path = request.getRequestURI();
        return highRiskPaths.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only capture for mutating methods on high-risk paths
        if (!isMutatingMethod(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap request to capture body
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        // Publish the masked body BEFORE delegating: downstream consumers
        // (AuditLogAspect during the request, RequestLogFilter in its finally)
        // read it from AuditRequestContext. The cached body is fully read at wrap
        // time, so masking here needs no response data.
        String body = wrappedRequest.getBody();
        if (StringUtils.hasText(body)) {
            try {
                AuditRequestContext.setRequestBody(maskSensitiveFields(body));
            } catch (Exception ex) {
                log.debug("Failed to parse/mask request body for {} {}: {}", method, path, ex.toString());
            }
        }
        chain.doFilter(wrappedRequest, response);
    }

    private boolean isMutatingMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String maskSensitiveFields(String jsonBody) {
        try {
            // Convert to Map for version-agnostic iteration
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = objectMapper.readValue(jsonBody, java.util.Map.class);
            maskMap(map);
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            log.debug("Failed to mask request body: {}", ex.toString());
            return "[MASKING_FAILED]";
        }
    }

    @SuppressWarnings("unchecked")
    private void maskMap(java.util.Map<String, Object> map) {
        if (map == null) {
            return;
        }
        java.util.List<String> keysToMask = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase();
            boolean shouldMask = maskPatterns.stream().anyMatch(pattern -> key.contains(pattern));
            if (shouldMask) {
                keysToMask.add(entry.getKey());
            } else {
                Object value = entry.getValue();
                if (value instanceof java.util.Map) {
                    maskMap((java.util.Map<String, Object>) value);
                } else if (value instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) value) {
                        if (item instanceof java.util.Map) {
                            maskMap((java.util.Map<String, Object>) item);
                        }
                    }
                }
            }
        }
        for (String key : keysToMask) {
            map.put(key, "[REDACTED]");
        }
    }

    /**
     * HttpServletRequest wrapper that caches the request body so it can be read multiple times.
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        String getBody() {
            if (cachedBody == null || cachedBody.length == 0) {
                return "";
            }
            try {
                return new String(cachedBody, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                return "";
            }
        }
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final java.io.ByteArrayInputStream buffer;

        CachedBodyServletInputStream(byte[] body) {
            this.buffer = new java.io.ByteArrayInputStream(body);
        }

        @Override
        public int read() throws IOException {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Not used
        }
    }
}