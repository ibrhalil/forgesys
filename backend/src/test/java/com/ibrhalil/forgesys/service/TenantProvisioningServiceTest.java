package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.RbacSeeder;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyRegisterResponse;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.TenantVerificationToken;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantVerificationTokenRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the K-21 two-phase provisioning flow. The DDL ({@link DataSource}) and
 * programmatic Flyway ({@link TenantMigrationSupport}) are mocked so the tests stay
 * H2-free and run in milliseconds. The phase 1 → phase 2 contract, token lifecycle and
 * the auto-verify bootstrap path are the focus.
 */
@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    private static final String SUBDOMAIN = "geba-klubu";
    private static final String SCHEMA_NAME = "tenant_geba_klubu";

    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantVerificationTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DataSource dataSource;
    @Mock private TenantMigrationSupport tenantMigrationSupport;
    @Mock private VerificationSender verificationSender;
    @Mock private ObjectProvider<RbacSeeder> rbacSeederProvider;
    @Mock private ObjectProvider<TenantProvisioningService> self;

    private TenantProvisioningService service;

    @BeforeEach
    void setUp() {
        // Manual construction: Mockito's @InjectMocks can mis-assign the two
        // ObjectProvider<?> constructor params (type erasure → same raw type).
        service = new TenantProvisioningService(
                companyRepository, userRepository, tokenRepository, passwordEncoder,
                dataSource, tenantMigrationSupport, verificationSender, rbacSeederProvider, self);
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://test.local");
        ReflectionTestUtils.setField(service, "tokenTtlHours", 24L);
        // REQUIRES_NEW self-proxy: createAdminUser is invoked through self.getObject(),
        // which in the unit test just returns the same (proxy-less) service instance.
        // Lenient because not every test reaches createAdminUser (e.g. token-error paths).
        lenient().when(self.getObject()).thenReturn(service);
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

        verify(verificationSender).send(anyString(), anyString());
    }

    @Test
    void createPendingCompany_takenSubdomain_throwsSubdomainTaken() {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.of(new Company()));

        assertThatThrownBy(() -> service.createPendingCompany(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPANY_SUBDOMAIN_TAKEN);

        verify(verificationSender, never()).send(anyString(), anyString());
    }

    @Test
    void verifyAndProvision_validToken_activatesAndCreatesAdmin() throws Exception {
        Company company = companyWithStatus(CompanyStatus.PROVISIONING);
        TenantVerificationToken token = validToken(company);
        when(tokenRepository.findByToken("good-token")).thenReturn(Optional.of(token));
        stubCreateSchema();
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(TenantVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyVerifyResponse response = service.verifyAndProvision("good-token");

        assertThat(response.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(token.getUsedAt()).isNotNull();

        verify(tenantMigrationSupport).migrateSchema(SCHEMA_NAME);
        verify(userRepository).save(any());
        verify(rbacSeederProvider).ifAvailable(any());
    }

    @Test
    void verifyAndProvision_unknownToken_throwsInvalid() {
        when(tokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyAndProvision("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_INVALID);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
    }

    @Test
    void verifyAndProvision_usedToken_throwsAlreadyUsed() {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        token.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(tokenRepository.findByToken("used")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndProvision("used"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_ALREADY_USED);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
    }

    @Test
    void verifyAndProvision_expiredToken_throwsExpired() {
        TenantVerificationToken token = validToken(companyWithStatus(CompanyStatus.PROVISIONING));
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndProvision("expired"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TENANT_TOKEN_EXPIRED);

        verify(tenantMigrationSupport, never()).migrateSchema(anyString());
    }

    @Test
    void provisionSystemTenant_autoVerifiesWithoutSendingMail() throws Exception {
        when(companyRepository.findBySubdomain(SUBDOMAIN)).thenReturn(Optional.empty());
        when(companyRepository.findBySchemaName(SCHEMA_NAME)).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(passwordEncoder.encode("secret-pw")).thenReturn("{sf-peppered}hash");
        // The token created at phase 1 is looked up by companyId (phase 1.5 of bootstrap)
        // and by its token value (phase 2). Wire both with the same instance so the chain holds.
        Company provisioningCompany = companyWithStatus(CompanyStatus.PROVISIONING);
        TenantVerificationToken token = validToken(provisioningCompany);
        when(tokenRepository.save(any(TenantVerificationToken.class))).thenAnswer(inv -> withTokenId(inv.getArgument(0)));
        when(tokenRepository.findByCompanyId(any(UUID.class))).thenReturn(Optional.of(token));
        when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
        stubCreateSchema();
        when(companyRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.of(companyWithStatus(CompanyStatus.ACTIVE)));

        Company result = service.provisionSystemTenant(request());

        assertThat(result).isNotNull();
        // Bootstrap auto-verify must NOT email the verification link.
        verify(verificationSender, never()).send(anyString(), anyString());
        verify(tenantMigrationSupport).migrateSchema(SCHEMA_NAME);
    }

    // --- helpers ---------------------------------------------------------

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
}
