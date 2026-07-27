package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformCompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AuditService auditService;

    private PlatformCompanyService platformCompanyService;

    @BeforeEach
    void setUp() {
        platformCompanyService = new PlatformCompanyService(companyRepository, auditService);
    }

    @Test
    void updateStatusRecordsAuditAfterContextRestore() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenReturn(company);

        platformCompanyService.updateStatus(id, CompanyStatus.SUSPENDED);

        verify(auditService).record("company_status_updated", "Company", id, "Acme");
    }

    @Test
    void updateStatusRejectsIllegalTransitionAndDoesNotAudit() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.TERMINATED);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThrows(BusinessException.class,
                () -> platformCompanyService.updateStatus(id, CompanyStatus.ACTIVE));

        verify(companyRepository, never()).save(any(Company.class));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    private Company companyFixture(UUID id, String name, CompanyStatus status) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setStatus(status);
        return company;
    }
}
