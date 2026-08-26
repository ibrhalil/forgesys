package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.PlatformPermissionCatalog;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountCreateRequest;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountCreatedResponse;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.PlatformApiKey;
import com.ibrhalil.forgesys.entity.PlatformApiKey_;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.entity.PlatformUserType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.PlatformApiKeyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * K-50 service accounts: a SERVICE {@code t_platform_users} row plus its
 * {@code t_platform_api_keys} row. The raw {@code <prefix>_<secret>} value exists
 * only in the creation response — at rest only its SHA-256 digest lives
 * ({@link TokenHasher}, RISK-30 pattern). Scopes are validated against the
 * in-code platform catalog (frozen decision #2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformServiceAccountService {

    /** Sort whitelist of the service-account list (v1: GET list only, no filter engine). */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(PlatformApiKey_.NAME, FilterFieldType.STRING, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .build();

    /** Unambiguous alphabet (no I/O/0/1) — the prefix is user-visible and typed. */
    private static final String PREFIX_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformUserRepository platformUserRepository;
    private final PlatformApiKeyRepository platformApiKeyRepository;
    private final PlatformAuditService platformAuditService;

    @Transactional
    public PlatformServiceAccountCreatedResponse create(PlatformServiceAccountCreateRequest request, UUID actorId) {
        List<String> scopes = validatedScopes(request.scopes());
        if (request.expiresAt() != null && !request.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "expiresAt must be in the future");
        }
        String prefix = generatePrefix();
        String rawKey = prefix + "_" + generateSecret();

        PlatformUser account = new PlatformUser();
        // Synthetic email: SERVICE identities never log in by email — uniqueness by UUID.
        account.setEmail("svc-" + UUID.randomUUID().toString().substring(0, 8) + "@service.internal");
        account.setDisplayName(request.name());
        account.setUserType(PlatformUserType.SERVICE);
        account.setEnabled(true);
        platformUserRepository.save(account);

        PlatformApiKey key = new PlatformApiKey();
        key.setPlatformUser(account);
        key.setName(request.name());
        key.setKeyPrefix(prefix);
        key.setKeyHash(TokenHasher.sha256Hex(rawKey));
        key.setScopes(String.join(",", scopes));
        key.setExpiresAt(request.expiresAt());
        platformApiKeyRepository.save(key);

        platformAuditService.record(actorId, actorTypeOf(actorId),
                PlatformAuditService.ACTION_API_KEY_CREATED, "PlatformApiKey", key.getId(), key.getName());
        log.info("Platform API key created: keyId={}, name={}, actorId={}", key.getId(), key.getName(), actorId);
        return new PlatformServiceAccountCreatedResponse(key.getId(), account.getId(), key.getName(),
                scopes, key.getKeyPrefix(), key.getExpiresAt(), rawKey);
    }

    @Transactional(readOnly = true)
    public Page<PlatformServiceAccountResponse> list(Pageable pageable) {
        return platformApiKeyRepository.findAll(pageable).map(this::toResponse);
    }

    /** Revokes the key AND disables the account (user-approved semantics); re-revoke → 404. */
    @Transactional
    public void revoke(UUID keyId, UUID actorId) {
        PlatformApiKey key = platformApiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("Service account not found with id: " + keyId));
        if (key.getRevokedAt() != null) {
            throw new ResourceNotFoundException("Service account not found with id: " + keyId);
        }
        key.setRevokedAt(OffsetDateTime.now());
        PlatformUser account = key.getPlatformUser();
        account.setEnabled(false);
        platformUserRepository.save(account);
        platformAuditService.record(actorId, actorTypeOf(actorId),
                PlatformAuditService.ACTION_API_KEY_REVOKED, "PlatformApiKey", key.getId(), key.getName());
        log.info("Platform API key revoked: keyId={}, actorId={}", key.getId(), actorId);
    }

    private List<String> validatedScopes(List<String> scopes) {
        for (String scope : scopes) {
            if (!PlatformPermissionCatalog.ALL_NAMES.contains(scope)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Unknown platform scope: '" + scope + "'. Valid scopes: " + PlatformPermissionCatalog.ALL_NAMES);
            }
        }
        return scopes.stream().distinct().toList();
    }

    private String actorTypeOf(UUID actorId) {
        if (actorId == null) {
            return PlatformAuditService.ACTOR_SYSTEM;
        }
        return platformUserRepository.findById(actorId)
                .map(user -> user.getUserType().name())
                .orElse(PlatformAuditService.ACTOR_SYSTEM);
    }

    private String generatePrefix() {
        StringBuilder prefix = new StringBuilder(PREFIX_LENGTH);
        for (int i = 0; i < PREFIX_LENGTH; i++) {
            prefix.append(PREFIX_ALPHABET.charAt(RANDOM.nextInt(PREFIX_ALPHABET.length())));
        }
        return prefix.toString();
    }

    /** 32 random bytes → 43-char base64url (secret chars may include '-'/'_'; split keys at the FIRST underscore). */
    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PlatformServiceAccountResponse toResponse(PlatformApiKey key) {
        PlatformUser account = key.getPlatformUser();
        return new PlatformServiceAccountResponse(
                key.getId(),
                account.getId(),
                key.getName(),
                List.of(key.getScopes().split(",")),
                key.getKeyPrefix(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getRevokedAt(),
                account.isEnabled(),
                key.getCreatedDate());
    }
}
