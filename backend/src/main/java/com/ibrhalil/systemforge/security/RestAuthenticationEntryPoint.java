package com.ibrhalil.systemforge.security;

import com.ibrhalil.systemforge.exception.ApiErrorFactory;
import com.ibrhalil.systemforge.exception.ApiErrorResponse;
import com.ibrhalil.systemforge.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Returns a uniform {@link ApiErrorResponse} (401, {@code auth_unauthenticated})
 * when an unauthenticated request reaches a protected resource. Replaces Spring
 * Security's default behavior of triggering a login redirect / 403 for stateless APIs.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        ApiErrorResponse body = ApiErrorFactory.of(ErrorCode.AUTH_UNAUTHENTICATED, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
