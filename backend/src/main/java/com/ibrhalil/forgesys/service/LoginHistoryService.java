package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.LoginHistory;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import com.ibrhalil.forgesys.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records every authentication attempt to {@code t_login_history} (K-19 layer 2);
 * failures carry the stable {@code ErrorCode} wire value as {@code reason} (forensics,
 * K-27). REQUIRES_NEW + best-effort — the write survives a failed-login throw and
 * never breaks the auth flow. Rationale: docs/CODE_NOTES.md (backend/service → LoginHistoryService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    /** {@code userId} null when the email is unknown; {@code reason} = ErrorCode.code() for failures. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String username, boolean success, String reason) {
        try {
            LoginHistory entry = new LoginHistory();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setSuccess(success);
            entry.setReason(reason);
            RequestContext.current().ifPresent(meta -> {
                entry.setIpAddress(meta.clientIp());
                entry.setUserAgent(meta.userAgent());
            });
            loginHistoryRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Failed to record login history (username={}, success={}, reason={})",
                    username, success, reason, ex);
        }
    }
}
