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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Two-phase tenant provisioning (K-21). Replaces the legacy single-phase
 * {@code provisionTenant} flow (removed alongside the {@code email_domain} column).
 *
 * <ol>
 *   <li>{@link #createPendingCompany} — light, fully transactional: validates
 *       uniqueness, inserts a {@code PROVISIONING} {@link Company} and a
 *       {@link TenantVerificationToken}, then hands the verification URL to
 *       {@link VerificationSender}. NO schema CREATE, NO Flyway, NO admin user —
 *       squatting is cheap.</li>
 *   <li>{@link #verifyAndProvision} — heavy, triggered by the user clicking the link:
 *       consumes the token, runs {@code CREATE SCHEMA} + programmatic Flyway, creates
 *       the admin user (with RBAC seed), flips the Company to {@code ACTIVE}, marks
 *       the token {@code usedAt}.</li>
 * </ol>
 *
 * <p>The system-tenant bootstrap (K-24, {@code SystemAdminBootstrapRunner}) avoids the
 * mail loop by calling {@link #provisionSystemTenant}, which runs both phases back to
 * back with verification sending suppressed.
 *
 * <p><strong>DEBT-10 (partial resolution):</strong> {@code createPendingCompany} is
 * fully transactional (DB writes only). {@code verifyAndProvision} is annotated
 * {@code @Transactional} for the Company/token/user writes, but {@code CREATE SCHEMA}
 * is an implicit commit in PostgreSQL so DDL escapes the transaction — partial-write
 * recovery is idempotency ({@code CREATE SCHEMA IF NOT EXISTS}, token {@code usedAt}
 * guard) rather than rollback.
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
    private final VerificationSender verificationSender;
    // Optional: RbacSeeder is @Profile("!test") — absent in tests, which never exercise provisioning.
    private final ObjectProvider<RbacSeeder> rbacSeederProvider;
    // Self-proxy reference so createAdminUser can be invoked through the Spring proxy,
    // which is required for @Transactional(REQUIRES_NEW) to take effect (RISK-26 fix:
    // the outer verifyAndProvision transaction holds a public-schema connection acquired
    // before TenantContext is switched; the admin user + RBAC seed must run in a fresh
    // transaction that re-resolves the tenant schema and acquires a connection with the
    // correct search_path).
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
        CompanyRegisterResponse response = createPendingCompanyInternal(request, /* sendVerification */ true);
        log.info("Pending tenant created, verification sent: subdomain={}, companyId={}",
                response.subdomain(), response.companyId());
        return new CompanyRegisterResponse(
                response.companyId(),
                response.name(),
                response.subdomain(),
                response.status(),
                "Doğrulama bağlantısı admin e-postasına gönderildi."
        );
    }

    /**
     * Phase 2 — promotes a {@code PROVISIONING} Company to {@code ACTIVE}: creates the
     * tenant schema, runs Flyway, creates the admin user, consumes the token.
     *
     * <p>[RISK-25] Token consumption is an atomic conditional UPDATE
     * ({@link TenantVerificationTokenRepository#claimToken}) rather than the previous
     * read-modify-write, so two concurrent verify requests sharing the same link cannot
     * both pass the {@code isUsed()} check and double-provision the tenant. The first
     * caller wins (claim returns 1); the second sees 0 and gets
     * {@code TENANT_TOKEN_ALREADY_USED}. Validity/expiry are still checked by SELECT
     * beforehand so the precise error code is preserved.
     */
    @Transactional
    public CompanyVerifyResponse verifyAndProvision(String token) {
        TenantVerificationToken verification = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_TOKEN_INVALID));
        if (verification.isUsed()) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }
        if (verification.isExpired(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_EXPIRED);
        }

        Company company = verification.getCompany();
        if (company.getStatus() != CompanyStatus.PROVISIONING) {
            // Token exists but the Company already moved past PROVISIONING — reject defensively.
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }

        // [RISK-25] Atomic claim: only one concurrent caller wins. A 0 count means
        // another verify request already stamped used_at between our SELECT and UPDATE.
        OffsetDateTime claimedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int claimedRows = tokenRepository.claimToken(token, claimedAt);
        if (claimedRows == 0) {
            throw new BusinessException(ErrorCode.TENANT_TOKEN_ALREADY_USED);
        }
        // Keep the managed entity in sync with the row the UPDATE just wrote (avoids a
        // redundant second UPDATE; claimToken already persisted used_at).
        verification.setUsedAt(claimedAt);

        log.info("Verifying tenant: subdomain={}, companyId={}", company.getSubdomain(), company.getId());

        String schemaName = company.getSchemaName();
        createSchema(schemaName);
        runTenantMigrations(schemaName);
        // RISK-26: set TenantContext BEFORE the REQUIRES_NEW proxy opens its Hibernate
        // session — CurrentTenantIdentifierResolver resolves at session-open time, so the
        // session must see the switched context to acquire a tenant-schema connection.
        // (createAdminUser also sets it defensively, and clears in finally.)
        TenantContext.setCurrentTenant(schemaName);
        User adminUser;
        try {
            adminUser = self.getObject().createAdminUser(schemaName, verification);
        } finally {
            TenantContext.clear();
        }

        // K-16 / Epic 3.0.A: FREE subscription + default module activations (permission
        // seed + activation record; pm needs no extra migration — baseline tables).
        // activateForCompany manages its own TenantContext + REQUIRES_NEW transaction.
        createDefaultSubscription(company);
        moduleActivationService.activateDefaultModules(company);

        company.setStatus(CompanyStatus.ACTIVE);
        Company saved = companyRepository.save(company);

        // K-47: sample data seed — afterCommit so the seed's REQUIRES_NEW transaction
        // sees the committed activation records + subscription (see registerSampleDataSeed).
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
     * Bootstrap-only auto-verify (K-24): runs phase 1 (no mail) then phase 2 in one
     * call so the {@code SystemAdminBootstrapRunner} can provision the reserved
     * {@code system} tenant without an email loop. Returns the activated Company.
     */
    @Transactional
    public Company provisionSystemTenant(CompanyRegisterRequest request) {
        CompanyRegisterResponse pending = createPendingCompanyInternal(request, /* sendVerification */ false);
        TenantVerificationToken token = tokenRepository.findByCompanyId(pending.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Verification token missing for just-created pending company: " + pending.companyId()));
        verifyAndProvision(token.getToken());
        return companyRepository.findById(pending.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Company missing after provisioning: " + pending.companyId()));
    }

    // --- internal helpers -------------------------------------------------

    /**
     * K-47: registers the sample-data seed to run AFTER the provisioning transaction
     * commits. The seed opens its own REQUIRES_NEW transaction (the outer session is
     * {@code public}-pinned, RISK-26) and that transaction must SEE the
     * {@code t_tenant_modules} activation records and the FREE subscription written by
     * this one — under read-committed an inner transaction cannot, so a
     * same-transaction call would fail the module gate (MODULE_NOT_ACTIVE) and the
     * plan chain (SUBSCRIPTION_NOT_FOUND) and be silently swallowed by the seed's own
     * fail-safe. afterCommit keeps the flow synchronous (same request) while ordering
     * the seed after the commit; the guard covers non-transactional callers (unit
     * tests). Fail-safe is two-layered: {@code seedForCompany} catches internally and
     * the callback catches again, so a synchronization surprise never escapes.
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

    private CompanyRegisterResponse createPendingCompanyInternal(CompanyRegisterRequest request,
                                                                 boolean sendVerification) {
        String schemaName = buildSchemaName(request.subdomain());
        validateUnique(request.subdomain(), schemaName);
        Company company = createCompany(request, schemaName, CompanyStatus.PROVISIONING);
        TenantVerificationToken token = issueToken(company, request);
        if (sendVerification) {
            verificationSender.send(request.adminEmail(), buildVerificationUrl(token.getToken()));
        }
        return new CompanyRegisterResponse(
                company.getId(),
                company.getName(),
                company.getSubdomain(),
                company.getStatus(),
                null
        );
    }

    private TenantVerificationToken issueToken(Company company, CompanyRegisterRequest request) {
        TenantVerificationToken token = new TenantVerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setCompany(company);
        token.setAdminEmail(request.adminEmail());
        // Pre-hash at phase 1 — phase 2 stores this verbatim (no re-hash).
        token.setAdminPasswordHash(passwordEncoder.encode(request.adminPassword()));
        token.setAdminFirstName(request.adminFirstName());
        token.setAdminLastName(request.adminLastName());
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(tokenTtlHours));
        return tokenRepository.save(token);
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
     * Creates the tenant's first admin user (with the pre-hashed credentials carried by
     * the verification token) and runs the RBAC seed; returns the persisted admin (its
     * id feeds the post-commit sample-data seed, K-47). Runs in its own transaction
     * (REQUIRES_NEW, invoked via the {@code self} proxy) because {@code verifyAndProvision}'s
     * outer transaction holds a {@code public}-schema connection acquired before
     * {@link TenantContext#setCurrentTenant(String)} is called; without a fresh
     * transaction the admin write + RBAC seed would be issued against {@code search_path=public}
     * and fail with "relation does not exist" (RISK-26).
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
            // Seed RBAC (permission catalog + Admin role), then explicitly grant Admin
            // to this first admin user — the ONLY place Admin is auto-assigned. Startup
            // seeding never touches user roles (privilege-escalation fix, 2026-08-16).
            // No-op in tests.
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
     * Writes the new tenant's initial FREE subscription (K-16). Real plan selection /
     * upgrades arrive in Faz 6. Fails fast when the plan row is missing —
     * {@code PlanSyncRunner} seeds plans before any provisioning can run.
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
