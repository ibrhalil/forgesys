package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.config.RbacSeeder;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyRegisterResponse;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantVerificationToken;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantVerificationTokenRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import com.ibrhalil.forgesys.service.mail.MailMessage;
import com.ibrhalil.forgesys.service.mail.MailSender;
import com.ibrhalil.forgesys.service.mail.MailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Two-phase tenant provisioning (K-21): {@link #createPendingCompany} (light,
 * transactional — no schema/DDL/admin) + {@link #verifyAndProvision} (heavy: CREATE
 * SCHEMA + Flyway + admin user + ACTIVE). Bootstrap (K-24) uses
 * {@link #provisionSystemTenant} (both phases, no mail). CREATE SCHEMA is an implicit
 * commit in PostgreSQL — recovery is idempotency, not rollback (DEBT-10 partial).
 * Rationale: docs/CODE_NOTES.md (backend/service → TenantProvisioningService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final String SCHEMA_PREFIX = "tenant_";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TenantVerificationTokenRepository tokenRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final TenantMigrationSupport tenantMigrationSupport;
    private final ModuleActivationService moduleActivationService;
    private final TenantSampleDataService sampleDataService;
    private final MailSender mailSender;
    // Optional: RbacSeeder is @Profile("!test") — absent in tests, which never exercise provisioning.
    private final ObjectProvider<RbacSeeder> rbacSeederProvider;
    // Self-proxy: @Transactional(REQUIRES_NEW) only takes effect through the proxy; the
    // outer session is public-pinned (RISK-26) — see createAdminUser.
    private final ObjectProvider<TenantProvisioningService> self;

    @Value("${forgesys.security.app-base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${forgesys.security.verification-token-ttl-hours:24}")
    private long tokenTtlHours;

    /**
     * Phase 1 — creates a {@code PROVISIONING} Company + verification token and emails
     * the link. The tenant schema does not exist yet.
     */
    @Transactional
    public CompanyRegisterResponse createPendingCompany(CompanyRegisterRequest request) {
        log.info("Creating pending tenant: subdomain={}", request.subdomain());
        PendingSignup pending = createPendingCompanyInternal(request, /* sendVerification */ true);
        log.info("Pending tenant created, verification sent: subdomain={}, companyId={}",
                pending.response().subdomain(), pending.response().companyId());
        return new CompanyRegisterResponse(
                pending.response().companyId(),
                pending.response().name(),
                pending.response().subdomain(),
                pending.response().status(),
                "Doğrulama bağlantısı admin e-postasına gönderildi."
        );
    }

    /**
     * Phase 2 — promotes a {@code PROVISIONING} Company to {@code ACTIVE}: schema +
     * Flyway + admin user + token consumption. Token claim is an atomic conditional
     * UPDATE ([RISK-25]); the raw token is hash-at-rest ([RISK-30]).
     */
    @Transactional
    public CompanyVerifyResponse verifyAndProvision(String rawToken) {
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        TenantVerificationToken verification = tokenRepository.findByToken(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_TOKEN_INVALID));
        if (verification.isUsed()) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }
        if (verification.isExpired(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_EXPIRED);
        }

        Company company = verification.getCompany();
        if (company.getStatus() != CompanyStatus.PROVISIONING) {
            // Company already moved past PROVISIONING — reject defensively.
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }

        // [RISK-25] Atomic claim: 0 rows means a concurrent verify already won.
        OffsetDateTime claimedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int claimedRows = tokenRepository.claimToken(tokenHash, claimedAt);
        if (claimedRows == 0) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }
        // Keep the managed entity in sync with the UPDATE (avoids a redundant second UPDATE).
        verification.setUsedAt(claimedAt);

        log.info("Verifying tenant: subdomain={}, companyId={}", company.getSubdomain(), company.getId());

        String schemaName = company.getSchemaName();
        createSchema(schemaName);
        runTenantMigrations(schemaName);
        // RISK-26: set TenantContext BEFORE the REQUIRES_NEW session opens — the
        // schema resolver runs at session-open time (createAdminUser also sets defensively).
        TenantContext.setCurrentTenant(schemaName);
        User adminUser;
        try {
            adminUser = self.getObject().createAdminUser(schemaName, verification);
        } finally {
            TenantContext.clear();
        }

        // [RISK-30] Drop the pre-hashed credentials now; a rollback restores the hash
        // (DEBT-10 recovery retries still work).
        verification.setAdminPasswordHash(null);

        // FREE subscription + default module activations (K-16); activateForCompany
        // manages its own TenantContext + transaction.
        createDefaultSubscription(company);
        moduleActivationService.activateDefaultModules(company);

        company.setStatus(CompanyStatus.ACTIVE);
        Company saved = companyRepository.save(company);

        // K-47: afterCommit so the seed's REQUIRES_NEW tx sees the committed rows.
        registerSampleDataSeed(saved, adminUser.getId());

        log.info("Tenant verified and provisioned: subdomain={}", saved.getSubdomain());
        return new CompanyVerifyResponse(
                saved.getId(),
                saved.getName(),
                saved.getSubdomain(),
                saved.getStatus(),
                "Organizasyon etkinleştirildi. Giriş yapabilirsiniz."
        );
    }

    /**
     * Bootstrap-only auto-verify (K-24): phase 1 (no mail) + phase 2 in one call; the
     * raw token moves in memory only ([RISK-30] — the DB keeps just its digest).
     */
    @Transactional
    public Company provisionSystemTenant(CompanyRegisterRequest request) {
        PendingSignup pending = createPendingCompanyInternal(request, /* sendVerification */ false);
        verifyAndProvision(pending.rawToken());
        return companyRepository.findById(pending.response().companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Company missing after provisioning: " + pending.response().companyId()));
    }

    // --- internal helpers -------------------------------------------------

    /** Phase 1 result + the RAW token ([RISK-30] hash-at-rest — raw never touches the DB). */
    private record PendingSignup(CompanyRegisterResponse response, String rawToken) {
    }

    /**
     * K-47: registers the seed to run AFTER commit — the seed's REQUIRES_NEW tx must
     * see the committed activation + subscription rows; a same-tx call would fail
     * those gates invisibly (read-committed) and be swallowed by the fail-safe.
     */
    private void registerSampleDataSeed(Company company, UUID adminUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("No active transaction synchronization — seeding sample data immediately");
            sampleDataService.seedForCompany(company, adminUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sampleDataService.seedForCompany(company, adminUserId);
                } catch (Exception e) {
                    log.warn("Sample data seeding failed for tenant {} — provisioning continues",
                            company.getSchemaName(), e);
                }
            }
        });
    }

    private PendingSignup createPendingCompanyInternal(CompanyRegisterRequest request,
                                                       boolean sendVerification) {
        String schemaName = buildSchemaName(request.subdomain());
        validateUnique(request.subdomain(), schemaName);
        Company company = createCompany(request, schemaName, CompanyStatus.PROVISIONING);
        String rawToken = UUID.randomUUID().toString();
        issueToken(company, request, rawToken);
        if (sendVerification) {
            mailSender.send(new MailMessage(
                    request.adminEmail(),
                    MailTemplate.TENANT_VERIFY,
                    buildVerificationUrl(rawToken),
                    request.adminFirstName(),
                    request.companyName(),
                    Duration.ofHours(tokenTtlHours)));
        }
        return new PendingSignup(new CompanyRegisterResponse(
                company.getId(),
                company.getName(),
                company.getSubdomain(),
                company.getStatus(),
                null
        ), rawToken);
    }

    private void issueToken(Company company, CompanyRegisterRequest request, String rawToken) {
        TenantVerificationToken token = new TenantVerificationToken();
        // [RISK-30] hash-at-rest: only the digest is persisted.
        token.setToken(TokenHasher.sha256Hex(rawToken));
        token.setCompany(company);
        token.setAdminEmail(request.adminEmail());
        // Pre-hash at phase 1 — phase 2 stores this verbatim (no re-hash).
        token.setAdminPasswordHash(passwordEncoder.encode(request.adminPassword()));
        token.setAdminFirstName(request.adminFirstName());
        token.setAdminLastName(request.adminLastName());
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(tokenTtlHours));
        tokenRepository.save(token);
    }

    private String buildVerificationUrl(String token) {
        String base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        return base + "/verify-tenant?token=" + token;
    }

    private String buildSchemaName(String subdomain) {
        String sanitized = subdomain.toLowerCase()
                .replaceAll("[^a-z0-9-]", "")
                .replace("-", "_");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Subdomain produces an invalid schema name");
        }
        String schemaName = SCHEMA_PREFIX + sanitized;
        if (!schemaName.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name derived from subdomain");
        }
        return schemaName;
    }

    private void validateUnique(String subdomain, String schemaName) {
        if (companyRepository.findBySubdomain(subdomain).isPresent()) {
            throw new BusinessException(ErrorCode.COMPANY_SUBDOMAIN_TAKEN);
        }
        if (companyRepository.findBySchemaName(schemaName).isPresent()) {
            throw new BusinessException(ErrorCode.COMPANY_SUBDOMAIN_TAKEN);
        }
    }

    private Company createCompany(CompanyRegisterRequest request, String schemaName, CompanyStatus status) {
        Company company = new Company();
        company.setName(request.companyName());
        company.setSubdomain(request.subdomain());
        company.setSchemaName(schemaName);
        company.setStatus(status);
        return companyRepository.save(company);
    }

    /**
     * Creates the tenant's first admin (pre-hashed credentials from the token) + RBAC
     * seed. REQUIRES_NEW via the self proxy: the outer session is {@code public}-pinned
     * (RISK-26) — without a fresh tx the admin write would run against
     * {@code search_path=public} and fail "relation does not exist".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createAdminUser(String schemaName, TenantVerificationToken verification) {
        try {
            TenantContext.setCurrentTenant(schemaName);

            User user = new User();
            user.setUsername(deriveUsername(verification.getAdminEmail()));
            user.setEmail(verification.getAdminEmail());
            // Pre-hashed at phase 1 — stored verbatim, no re-hash.
            user.setPassword(verification.getAdminPasswordHash());
            user.setEmailVerified(true);

            UserAccount account = new UserAccount();
            account.setUser(user);
            user.setUserAccount(account);

            UserProfile profile = new UserProfile();
            profile.setUser(user);
            profile.setFirstName(verification.getAdminFirstName());
            profile.setLastName(verification.getAdminLastName());
            user.setUserProfile(profile);

            userRepository.save(user);
            // assignAdminTo is the ONLY automatic Admin grant — startup seeding never
            // touches user roles (2026-08-16 privilege-escalation fix). No-op in tests.
            rbacSeederProvider.ifAvailable(seeder -> {
                seeder.seedForCurrentTenant();
                seeder.assignAdminTo(user);
            });
            log.info("Admin user created for tenant schema: {}", schemaName);
            return user;
        } finally {
            TenantContext.clear();
        }
    }

    private String deriveUsername(String email) {
        int at = email.indexOf('@');
        String prefix = at > 0 ? email.substring(0, at) : email;
        if (prefix.length() > 70) {
            prefix = prefix.substring(0, 70);
        }
        return prefix;
    }

    private void createSchema(String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Schema created: {}", schemaName);
        } catch (Exception e) {
            log.error("Error creating schema: {}", schemaName, e);
            throw new RuntimeException("Could not create schema: " + schemaName, e);
        }
    }

    private void runTenantMigrations(String schemaName) {
        tenantMigrationSupport.migrateSchema(schemaName);
    }

    /**
     * Writes the initial FREE subscription (K-16); fails fast when the plan row is
     * missing ({@code PlanSyncRunner} seeds plans before any provisioning runs).
     */
    private void createDefaultSubscription(Company company) {
        Plan freePlan = planRepository.findByKey(PlanDefinition.FREE.key())
                .orElseThrow(() -> new IllegalStateException(
                        "FREE plan row missing; PlanSyncRunner must seed plans before provisioning"));
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        subscriptionRepository.save(subscription);
    }
}
