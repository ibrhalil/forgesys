package com.ibrhalil.forgesys.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the K-21 two-phase provisioning flow. The DDL ({@link DataSource}) and
 * programmatic Flyway ({@link TenantMigrationSupport}) are mocked so the tests stay
 * H2-free and run in milliseconds. The phase 1 → phase 2 contract and the token
 * lifecycle are the focus.
 */
@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    private static final String SUBDOMAIN = "geba-klubu";
    private static final String SCHEMA_NAME = "tenant_geba_klubu";
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantVerificationTokenRepository tokenRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DataSource dataSource;
    @Mock private TenantMigrationSupport tenantMigrationSupport;
    @Mock private ModuleActivationService moduleActivationService;
    @Mock private TenantSampleDataService sampleDataService;
    @Mock private MailSender mailSender;
    @Mock private ObjectProvider<RbacSeeder> rbacSeederProvider;
    @Mock private ObjectProvider<TenantProvisioningService> self;

    private TenantProvisioningService service;

    @BeforeEach
    void setUp() {
        // Manual construction: Mockito's @InjectMocks can mis-assign the two
        // ObjectProvider<?> constructor params (type erasure → same raw type).
        service = new TenantProvisioningService(
                companyRepository, userRepository, tokenRepository, planRepository, subscriptionRepository,
                passwordEncoder, dataSource, tenantMigrationSupport, moduleActivationService,
                sampleDataService, mailSender, rbacSeederProvider, self);
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://test.local");
        ReflectionTestUtils.setField(service, "tokenTtlHours", 24L);
        // REQUIRES_NEW self-proxy: createAdminUser is invoked through self.getObject(),
        // which in the unit test just returns the same (proxy-less) service instance.
        // Lenient because not every test reaches createAdminUser (e.g. token-error paths).
        lenient().when(self.getObject()).thenReturn(service);
        // K-47: verifyAndProvision registers the sample-data seed as an afterCommit
        // synchronization when one is active — emulate the transactional caller.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** Fires the registered afterCommit callbacks (the production commit point). */
    private void commitProvisioning() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void createPendingCompany_createsProvisioningCompanyAndSendsLink() {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.empty());
        when(companyRepository.findBySchemaName(SCHEMA_NAME)).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(passwordEncoder.encode("secret-pw")).thenReturn("{sf-peppered}hash");
        when(tokenRepository.save(any(TenantVerificationToken.class)))
                .thenAnswer(inv -> withTokenId(inv.getArgument(0)));

        CompanyRegisterResponse response = service.createPendingCompany(request());

        assertThat(response.status()).isEqualTo(CompanyStatus.PROVISIONING);
        assertThat(response.subdomain()).isEqualTo(SUBDOMAIN);

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());
        Company saved = companyCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.PROVISIONING);
        assertThat(saved.getSchemaName()).isEqualTo(SCHEMA_NAME);

        ArgumentCaptor<TenantVerificationToken> tokenCaptor = ArgumentCaptor.forClass(TenantVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        TenantVerificationToken token = tokenCaptor.getValue();
        assertThat(token.getAdminEmail()).isEqualTo("ali@gmail.com");
        assertThat(token.getAdminPasswordHash()).isEqualTo("{sf-peppered}hash");
        assertThat(token.getExpiresAt()).isNotNull();
        assertThat(token.getUsedAt()).isNull();

        verify(mailSender).send(any(MailMessage.class));
    }

    /**
     * [RISK-30] Hash-at-rest: the persisted row carries ONLY the SHA-256 digest of the
     * token; the emailed link carries the raw value. The two must correspond — a link
     * recipient's verify request hashes back to the stored digest.
     */
    @Test
    void createPendingCompany_persistsOnlyTheTokenDigest() {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.empty());
        when(companyRepository.findBySchemaName(SCHEMA_NAME)).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(passwordEncoder.encode("secret-pw")).thenReturn("{sf-peppered}hash");
        ArgumentCaptor<TenantVerificationToken> tokenCaptor = ArgumentCaptor.forClass(TenantVerificationToken.class);
        when(tokenRepository.save(tokenCaptor.capture())).thenAnswer(inv -> withTokenId(inv.getArgument(0)));

        service.createPendingCompany(request());

        // Raw token comes from the mailed action URL — the only place it exists.
        ArgumentCaptor<MailMessage> mailCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        String url = mailCaptor.getValue().actionUrl();
        String rawToken = url.substring(url.indexOf("token=") + "token=".length());

        String persisted = tokenCaptor.getValue().getToken();
        assertThat(persisted).isNotEqualTo(rawToken);
        assertThat(persisted).isEqualTo(TokenHasher.sha256Hex(rawToken));
    }

    @Test
    void createPendingCompany_takenSubdomain_throwsSubdomainTaken() {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.of(new Company()));

        assertThatThrownBy(() -> service.createPendingCompany(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_SUBDOMAIN_TAKEN);

        verify(mailSender, never()).send(any(MailMessage.class));
    }

    @Test
    void verifyAndProvision_validToken_activatesAndCreatesAdmin() throws Exception {
        Company company = companyWithStatus(CompanyStatus.PROVISIONING);
        TenantVerificationToken token = validToken(company);
        when(tokenRepository.findByToken(hashOf("good-token"))).thenReturn(Optional.of(token));
        // [RISK-25] atomic claim returns 1 (caller wins the race).
        when(tokenRepository.claimToken(eq(hashOf("good-token")), any(OffsetDateTime.class))).thenReturn(1);
        stubCreateSchema();
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        stubFreePlan();
        stubAdminUserSave();

        CompanyVerifyResponse response = service.verifyAndProvision("good-token");

        assertThat(response.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(token.getUsedAt()).isNotNull();
        // [RISK-30] the admin user has been created — the token row must no longer
        // carry the pre-hashed admin credentials.
        assertThat(token.getAdminPasswordHash()).isNull();

        verify(tenantMigrationSupport).migrateSchema(SCHEMA_NAME);
        verify(userRepository).save(any());
        // K-16: provisioning writes the initial FREE subscription and activates the
        // default modules for the new tenant.
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getPlan().getKey()).isEqualTo("free");
        assertThat(subscriptionCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(moduleActivationService).activateDefaultModules(company);
        // The provisioning callback must seed the catalog AND explicitly grant Admin to
        // the new user — startup seeding no longer assigns roles (privilege-escalation
        // fix, 2026-08-16), so this is the only Admin grant path.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Consumer<RbacSeeder>> seederCaptor =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(rbacSeederProvider).ifAvailable(seederCaptor.capture());
        RbacSeeder seeder = org.mockito.Mockito.mock(RbacSeeder.class);
        seederCaptor.getValue().accept(seeder);
        verify(seeder).seedForCurrentTenant();
        verify(seeder).assignAdminTo(any(User.class));
        // [RISK-25] the claim UPDATE persists used_at; the service no longer re-saves the
        // token afterwards.
        verify(tokenRepository, never()).save(any(TenantVerificationToken.class));
        // K-47: the sample-data seed fires after the provisioning transaction commits,
        // with the new admin's id (the Linear onboarding pattern).
        commitProvisioning();
        verify(sampleDataService).seedForCompany(company, ADMIN_ID);
    }

    /**
     * K-47 fail-safe proof: even when the sample-data seed throws, provisioning has
     * already succeeded — the afterCommit callback swallows the failure and the
     * Company stays ACTIVE.
     */
    @Test
    void verifyAndProvision_sampleDataSeedFails_provisioningStillSucceeds() throws Exception {
        Company company = companyWithStatus(CompanyStatus.PROVISIONING);
        TenantVerificationToken token = validToken(company);
        when(tokenRepository.findByToken(hashOf("good-token"))).thenReturn(Optional.of(token));
        when(tokenRepository.claimToken(eq(hashOf("good-token")), any(OffsetDateTime.class))).thenReturn(1);
        stubCreateSchema();
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        stubFreePlan();
        stubAdminUserSave();
        doThrow(new RuntimeException("seed exploded"))
                .when(sampleDataService).seedForCompany(any(Company.class), any());

        CompanyVerifyResponse response = service.verifyAndProvision("good-token");

        assertThat(response.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        // The exception must not escape the afterCommit callback.
        commitProvisioning();
        verify(sampleDataService).seedForCompany(company, ADMIN_ID);
    }

    @Test
    void verifyAndProvision_unknownToken_throwsInvalid() {
        when(tokenRepository.findByToken(hashOf("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndProvision("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_INVALID);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
    }

    @Test
    void verifyAndProvision_usedToken_throwsAlreadyUsed() {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        token.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(tokenRepository.findByToken(hashOf("used"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndProvision("used"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_ALREADY_USED);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
        // [RISK-25] the SELECT-time isUsed() check short-circuits before the atomic claim.
        verify(tokenRepository, never()).claimToken(anyString(), any(OffsetDateTime.class));
    }

    /**
     * [RISK-25] Race loser: another concurrent verify request claimed the token between
     * our SELECT (saw {@code used_at = null}) and our UPDATE. The conditional claim
     * returns 0 and the service maps that to {@code TENANT_TOKEN_ALREADY_USED} — the
     * tenant is not double-provisioned (no CREATE SCHEMA, no admin user, no status flip).
     */
    @Test
    void verifyAndProvision_concurrentClaimLost_throwsAlreadyUsed() throws Exception {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        when(tokenRepository.findByToken(hashOf("contended"))).thenReturn(Optional.of(token));
        when(tokenRepository.claimToken(eq(hashOf("contended")), any(OffsetDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.verifyAndProvision("contended"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_ALREADY_USED);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
        verify(userRepository, never()).save(any());
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void verifyAndProvision_expiredToken_throwsExpired() {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(tokenRepository.findByToken(hashOf("expired"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndProvision("expired"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_EXPIRED);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
    }

    /**
     * End-to-end phase 1 → phase 2 chain ([RISK-30]): the raw token exists ONLY in the
     * mailed action URL; {@code verifyAndProvision} hashes it back to the digest phase 1
     * persisted and claims exactly that digest. Replaces the former K-24 bootstrap
     * auto-verify path (removed by K-50 F3).
     */
    @Test
    void verifyAndProvision_claimRunsAgainstTheDigestPhase1Persisted() throws Exception {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.empty());
        when(companyRepository.findBySchemaName(SCHEMA_NAME)).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(passwordEncoder.encode("secret-pw")).thenReturn("{sf-peppered}hash");
        // [RISK-30] phase 1 hands the raw token to phase 2 only via the emailed link;
        // phase 2 hashes it for lookup/claim. Capture the actually-issued digest so
        // the stubs follow the production chain instead of a fabricated one.
        AtomicReference<TenantVerificationToken> issued = new AtomicReference<>();
        when(tokenRepository.save(any(TenantVerificationToken.class))).thenAnswer(inv -> {
            TenantVerificationToken saved = withTokenId(inv.getArgument(0));
            issued.set(saved);
            return saved;
        });
        when(tokenRepository.findByToken(anyString()))
                .thenAnswer(inv -> Optional.of(issued.get()));
        when(tokenRepository.claimToken(anyString(), any(OffsetDateTime.class))).thenReturn(1);
        stubCreateSchema();
        stubFreePlan();
        stubAdminUserSave();

        service.createPendingCompany(request());
        // The raw token lives only in the emailed link — extract it like a recipient.
        ArgumentCaptor<MailMessage> mailCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        String url = mailCaptor.getValue().actionUrl();
        String rawToken = url.substring(url.indexOf("token=") + "token=".length());

        CompanyVerifyResponse response = service.verifyAndProvision(rawToken);

        assertThat(response.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(TokenHasher.sha256Hex(rawToken)).isEqualTo(issued.get().getToken());
        // The claim must run against the digest phase 1 actually persisted (not a
        // re-read of the row — the raw token never returns from the DB).
        verify(tokenRepository).claimToken(eq(issued.get().getToken()), any(OffsetDateTime.class));
        verify(tenantMigrationSupport).migrateSchema(SCHEMA_NAME);
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(moduleActivationService).activateDefaultModules(any(Company.class));
        // K-47: provisioning runs the afterCommit sample-data seed with the admin id.
        commitProvisioning();
        verify(sampleDataService).seedForCompany(any(Company.class), eq(ADMIN_ID));
    }

    /**
     * K-16: provisioning fails fast when the FREE plan row is missing — PlanSyncRunner
     * must have seeded plans; a silent skip would leave the tenant without a subscription.
     */
    @Test
    void verifyAndProvision_missingFreePlan_failsFast() throws Exception {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        when(tokenRepository.findByToken(hashOf("good-token"))).thenReturn(Optional.of(token));
        when(tokenRepository.claimToken(eq(hashOf("good-token")), any(OffsetDateTime.class))).thenReturn(1);
        stubCreateSchema();
        when(planRepository.findByKey("free")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndProvision("good-token"))
                .isInstanceOf(IllegalStateException.class);

        verify(moduleActivationService, never()).activateDefaultModules(any());
    }

    // --- helpers ---------------------------------------------------------

    /** [RISK-30] the digest the service derives from a presented raw token. */
    private static String hashOf(String raw) {
        return TokenHasher.sha256Hex(raw);
    }

    private CompanyRegisterRequest request() {
        return new CompanyRegisterRequest(
                "Gebze Klübü", SUBDOMAIN, "ali@gmail.com", "secret-pw", "Ali", "Yılmaz");
    }

    private Company companyWithStatus(CompanyStatus status) {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Gebze Klübü");
        company.setSubdomain(SUBDOMAIN);
        company.setSchemaName(SCHEMA_NAME);
        company.setStatus(status);
        return company;
    }

    private TenantVerificationToken validToken(Company company) {
        TenantVerificationToken token = new TenantVerificationToken();
        token.setId(UUID.randomUUID());
        token.setToken(UUID.randomUUID().toString());
        token.setCompany(company != null ? company : companyWithStatus(CompanyStatus.PROVISIONING));
        token.setAdminEmail("ali@gmail.com");
        token.setAdminPasswordHash("{sf-peppered}hash");
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        return token;
    }

    private Company withId(Company company) {
        company.setId(UUID.randomUUID());
        return company;
    }

    private TenantVerificationToken withTokenId(TenantVerificationToken token) {
        token.setId(UUID.randomUUID());
        return token;
    }

    private void stubCreateSchema() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        Statement statement = org.mockito.Mockito.mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        doNothing().when(tenantMigrationSupport).migrateSchema(anyString());
    }

    private void stubFreePlan() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setKey("free");
        plan.setName("Free");
        plan.setRank(0);
        plan.setActive(true);
        when(planRepository.findByKey("free")).thenReturn(Optional.of(plan));
    }

    /** The mock save does not run @GeneratedValue — stamp the id the seed hook needs (K-47). */
    private void stubAdminUserSave() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(ADMIN_ID);
            return user;
        });
    }
}
