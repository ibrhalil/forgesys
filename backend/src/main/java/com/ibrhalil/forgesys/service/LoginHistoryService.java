package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.LoginHistory;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import com.ibrhalil.forgesys.web.RequestContext;
import com.ibrhalil.forgesys.web.RequestMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records authentication attempts to {@code t_login_history} (K-19 layer 2).
 * Both successful and failed logins are written &mdash; a failed attempt carries
 * the stable {@code ErrorCode} wire value as its {@code reason}
 * (e.g. {@code auth_bad_credentials}, {@code auth_account_locked}); the client
 * response stays uniform, but the stored reason enables brute-force / anomaly
 * forensics (K-27).
 *
 * <p>The client IP and User-Agent are pulled from {@link RequestContext} (populated
 * by {@code RequestMetadataFilter}); when absent (no web request, e.g. a bootstrap
 * or test path) they are left null.
 *
 * <p><strong>Transaction isolation:</strong> the write runs in a
 * {@link Propagation#REQUIRES_NEW} transaction so it commits independently of the
 * caller's outcome &mdash; a failed login ({@code AuthService.login} throws) still
 * records the attempt. The write is best-effort: any failure is logged and
 * swallowed so audit logging can never break the authentication flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    /**
     * @param userId   the authenticating user's id, or {@code null} when the email
     *                 is unknown (no matching user) &mdash; the {@code username}
     *                 column still holds the attempted email
     * @param username the attempted email (or the user's email); never null
     * @param success  whether the authentication succeeded
     * @param reason   stable {@code ErrorCode.code()} for failures, {@code null} for success
     */
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
