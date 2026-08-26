package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.RequestLog;
import com.ibrhalil.forgesys.persistence.repository.RequestLogRepository;
import com.ibrhalil.forgesys.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes request/trace rows to {@code t_request_logs} (K-19 layer 3 + K-27);
 * REQUIRES_NEW + best-effort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final RequestLogRepository requestLogRepository;
    private final ObjectProvider<RequestLogService> self;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String traceId, String method, String path, Integer status, Long durationMs,
                       UUID userId, String username, String ipAddress, String userAgent, String requestBody) {
        try {
            self.getObject().recordInNewTx(traceId, method, path, status, durationMs, userId, username, ipAddress, userAgent, requestBody);
        } catch (RuntimeException ex) {
            log.debug("Failed to record request log (traceId={}, method={}, path={}): {}", traceId, method, path, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTx(String traceId, String method, String path, Integer status, Long durationMs,
                              UUID userId, String username, String ipAddress, String userAgent, String requestBody) {
        try {
            RequestLog entry = new RequestLog();
            entry.setTraceId(traceId);
            entry.setMethod(method);
            entry.setPath(path);
            entry.setStatus(status);
            entry.setDurationMs(durationMs);
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setIpAddress(ipAddress);
            entry.setUserAgent(userAgent);
            entry.setRequestBody(requestBody);

            if (entry.getUserId() == null || entry.getUsername() == null) {
                resolveActor(entry);
            }

            if (entry.getIpAddress() == null || entry.getUserAgent() == null || entry.getTraceId() == null) {
                RequestContext.current().ifPresent(meta -> {
                    if (entry.getTraceId() == null) entry.setTraceId(meta.traceId());
                    if (entry.getIpAddress() == null) entry.setIpAddress(meta.clientIp());
                    if (entry.getUserAgent() == null) entry.setUserAgent(meta.userAgent());
                });
            }

            requestLogRepository.save(entry);
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            // Table doesn't exist yet in this schema (SQLState 42P01) — skip gracefully.
            if (ex.getCause() instanceof SQLGrammarException sqlEx &&
                    sqlEx.getSQLException().getSQLState().equals("42P01")) {
                log.debug("Request log table not yet created (K-27 pending), skipping log for traceId={}", traceId);
            } else {
                throw ex;
            }
        }
    }

    private void resolveActor(RequestLog entry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.ibrhalil.forgesys.security.CustomUserDetails user) {
            if (entry.getUserId() == null) entry.setUserId(user.getUserId());
            if (entry.getUsername() == null) entry.setUsername(user.getEmail());
        }
    }
}