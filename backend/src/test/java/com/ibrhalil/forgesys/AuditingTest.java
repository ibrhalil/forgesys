package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        company.setEmailDomain("test.com");
        company.setSchemaName("tenant_test");
        company.setStatus(CompanyStatus.ACTIVE);

        companyRepository.saveAndFlush(company);

        assertThat(company.getCreatedDate()).isNotNull();
        assertThat(company.getUpdatedAt()).isNotNull();
        assertThat(company.getCreatedBy()).isEqualTo("system");
        assertThat(company.getUpdatedBy()).isEqualTo("system");
    }
}


