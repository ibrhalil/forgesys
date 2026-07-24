package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditingTest {

    @Autowired
    private DateTimeProvider dateTimeProvider;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void dateTimeProviderBeanResolvesAndReturnsNow() {
        assertThat(dateTimeProvider).isNotNull();
        assertThat(dateTimeProvider.getNow()).isPresent();
    }

    @Test
    @Transactional
    void companyInsertPopulatesAuditFields() {
        Company company = new Company();
        company.setName("Test Co");
        company.setSubdomain("test");
        company.setSchemaName("tenant_test");
        company.setStatus(CompanyStatus.ACTIVE);

        companyRepository.saveAndFlush(company);

        assertThat(company.getCreatedDate()).isNotNull();
        assertThat(company.getUpdatedAt()).isNotNull();
        assertThat(company.getCreatedBy()).isEqualTo("system");
        assertThat(company.getUpdatedBy()).isEqualTo("system");
    }

    /**
     * [RISK-33] When an authenticated principal is present, the audit fields record
     * that user's id (not "system"). Falls back to "system" only when there is no
     * authenticated user (signup/provisioning/startup).
     */
    @Test
    @Transactional
    void companyInsertRecordsAuthenticatedUserIdAsAuditor() {
        UUID userId = UUID.randomUUID();
        Set<GrantedAuthority> noAuthorities = Set.of();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId, "auditor@tenant.test", null, true, true, true, true, noAuthorities, null),
                null, noAuthorities));
        try {
            Company company = new Company();
            company.setName("Audited Co");
            company.setSubdomain("audited");
            company.setSchemaName("tenant_audited");
            company.setStatus(CompanyStatus.ACTIVE);

            companyRepository.saveAndFlush(company);

            assertThat(company.getCreatedBy()).isEqualTo(userId.toString());
            assertThat(company.getUpdatedBy()).isEqualTo(userId.toString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}


