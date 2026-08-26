package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.PlatformSwitchExchangeResponse;
import com.ibrhalil.forgesys.dto.PlatformSwitchStartResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.LastAdminGuard;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.security.TokenBlacklistService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.security.jwt.PlatformAuthProperties;
import com.ibrhalil.forgesys.security.platformswitch.PlatformSwitchStore;
import com.ibrhalil.forgesys.security.platformswitch.SwitchCodeData;
import com.ibrhalil.forgesys.service.mail.MailLinkBuilder;
import com.ibrhalil.forgesys.tenant.TenantContextExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * K-50 F6 tenant switch / impersonation (token exchange, NO API mirroring —
 * frozen decision #4). start(): platform-side — validates the target company,
 * picks the earliest-created admin-capable user (RISK-35 all-permissions closure
 * via {@link LastAdminGuard}) and stores a one-time Redis code. exchange():
 * tenant-side (subdomain host) — atomically claims the code, enforces the
 * RISK-19 schema symmetry and mints the impersonation JWT (frozen decision #5
 * claims; authorities resolved like a real login of the target user at that
 * moment). end(): existing logout — jti blacklist + concurrency-guard clear.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSwitchService {

    public static final String ACTION_SWITCH_STARTED = "platform_switch_started";
    public static final String ACTION_SWITCH_REDEEMED = "platform_switch_redeemed";
    public static final String ACTION_SWITCH_ENDED = "platform_switch_ended";
    /** Tenant-side audit action (t_audit_logs, actor = platform identity). */
    public static final String ACTION_IMPERSONATION_STARTED = "impersonation_session_started";

    private static final Duration CODE_TTL = Duration.ofSeconds(30);
    /** Reservation outlives the code so the guard covers claim + exchange latency. */
    private static final Duration RESERVATION_TTL = Duration.ofSeconds(35);

    private final CompanyRepository companyRepository;
    private final PlatformUserRepository platformUserRepository;
    private final UserRepository userRepository;
    private final LastAdminGuard lastAdminGuard;
    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;
    private final PlatformAuthProperties platformAuthProperties;
    private final PlatformSwitchStore switchStore;
    private final TokenBlacklistService tokenBlacklistService;
    private final PlatformAuditService platformAuditService;
    private final AuditService auditService;
    private final MailLinkBuilder mailLinkBuilder;

    public PlatformSwitchStartResponse start(UUID companyId, String reason) {
        CustomUserDetails actor = currentPlatformActor();
        Company company = TenantContextExecutor.withoutTenantContext(() -> companyRepository.findById(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_ACTIVE);
        }
        User target = TenantContextExecutor.inTenantContext(company.getSchemaName(),
                () -> earliestAdminCapableUser());
        String actorType = platformUserRepository.findById(actor.getUserId())
                .map(platformUser -> platformUser.getUserType().name())
                .orElse(PlatformAuditService.ACTOR_HUMAN);
        if (!switchStore.tryReserveActor(actor.getUserId(), RESERVATION_TTL)) {
            throw new BusinessException(ErrorCode.PLATFORM_SWITCH_ALREADY_ACTIVE);
        }
        String rawCode;
        try {
            rawCode = switchStore.issue(new SwitchCodeData(
                    companyId, company.getSchemaName(), target.getId(),
                    actor.getUserId(), actorType, reason), CODE_TTL);
        } catch (RuntimeException ex) {
            switchStore.releaseReservation(actor.getUserId());
            throw ex;
        }
        platformAuditService.record(actor.getUserId(), actorType, ACTION_SWITCH_STARTED,
                "company", companyId, reason);
        TenantContextExecutor.inTenantContext(company.getSchemaName(), () ->
                auditService.recordWithActor(ACTION_SWITCH_STARTED, "company", companyId, null,
                        actor.getUserId(), actor.getEmail()));
        return new PlatformSwitchStartResponse(rawCode, mailLinkBuilder.tenantBaseUrl(company.getSchemaName()));
    }

    public PlatformSwitchExchangeResponse exchange(String rawCode) {
        String ctxTenant = TenantContext.getCurrentTenant().orElse("public");
        SwitchCodeData data = switchStore.claim(rawCode)
                .orElseThrow(() -> new AuthException(ErrorCode.PLATFORM_SWITCH_CODE_INVALID));
        if (!data.schemaName().equals(ctxTenant)) {
            // RISK-19 symmetry: the code was minted for another tenant — the code is
            // burned (claimed above), so this is a dead end for cross-tenant probing.
            throw new AuthException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        Company company = TenantContextExecutor.withoutTenantContext(
                () -> companyRepository.findById(data.companyId()).orElse(null));
        if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
            throw new AuthException(ErrorCode.PLATFORM_SWITCH_CODE_INVALID);
        }
        User target = userRepository.findById(data.targetUserId())
                .orElseThrow(() -> new AuthException(ErrorCode.PLATFORM_SWITCH_CODE_INVALID));
        UserAccount account = target.getUserAccount();
        if (account == null || !account.isEnabled()
                || (account.getLockedUntil() != null && account.getLockedUntil().isAfter(OffsetDateTime.now()))) {
            throw new AuthException(ErrorCode.PLATFORM_SWITCH_CODE_INVALID);
        }

        Set<GrantedAuthority> authorities = userDetailsService.resolveAuthorities(target.getId());
        List<String> authorityNames = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        String actorDisplay = platformUserRepository.findById(data.actorId())
                .map(platformUser -> StringUtils.hasText(platformUser.getDisplayName())
                        ? platformUser.getDisplayName() : platformUser.getEmail())
                .orElse(data.actorId().toString());
        long ttlMinutes = platformAuthProperties.effectiveImpersonationTtlMinutes();
        String jti = UUID.randomUUID().toString();
        String token = tokenProvider.generateImpersonationToken(
                target.getId().toString(), target.getEmail(), data.schemaName(),
                authorityNames, data.actorId().toString(), actorDisplay, jti, ttlMinutes);
        switchStore.activate(data.actorId(), jti, Duration.ofMinutes(ttlMinutes));

        platformAuditService.record(data.actorId(), data.actorType(), ACTION_SWITCH_REDEEMED,
                "company", data.companyId(), data.reason());
        auditService.recordWithActor(ACTION_IMPERSONATION_STARTED, "user", target.getId(), target.getEmail(),
                data.actorId(), actorDisplay);
        return new PlatformSwitchExchangeResponse(token, "Bearer", ttlMinutes * 60, target.getId(), target.getEmail());
    }

    /** Impersonation logout: jti blacklist (no refresh token exists) + guard clear. */
    public void end(UUID actorId, String jti) {
        if (StringUtils.hasText(jti)) {
            tokenBlacklistService.blacklist(jti, platformAuthProperties.effectiveImpersonationTtlMinutes() * 60);
            switchStore.clearActiveIfCurrent(actorId, jti);
        }
        String actorType = platformUserRepository.findById(actorId)
                .map(platformUser -> platformUser.getUserType().name())
                .orElse(null);
        platformAuditService.record(actorId, actorType, ACTION_SWITCH_ENDED, null, null, null);
    }

    /** Earliest-created enabled holder of an admin-capable role (RISK-35 closure). */
    private User earliestAdminCapableUser() {
        Set<UUID> adminRoleIds = lastAdminGuard.adminCapableRoleIds();
        if (!adminRoleIds.isEmpty()) {
            List<User> candidates = userRepository.findFirstEnabledByRoleIds(adminRoleIds,
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "createdDate", "id")));
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        throw new BusinessException(ErrorCode.PLATFORM_NO_ADMIN_IN_TENANT);
    }

    /** The @PreAuthorize gate is primary; this is defense-in-depth for the service entry. */
    private CustomUserDetails currentPlatformActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details
                && details.isPlatform()) {
            return details;
        }
        throw new AuthException(ErrorCode.AUTH_UNAUTHENTICATED);
    }
}
