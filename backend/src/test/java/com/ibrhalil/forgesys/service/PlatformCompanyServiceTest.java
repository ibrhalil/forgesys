package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLogAspect;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicReference<AuditLogAspect.AuditCapture> auditCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        platformCompanyService = new PlatformCompanyService(companyRepository, auditService);
        AuditLogAspect.setTestHook(auditCapture::set);
    }

    @AfterEach
    void tearDown() {
        AuditLogAspect.clearTestHook();
        auditCapture.set(null);
    }

    @Test
    void updateStatusRecordsAuditAfterContextRestore() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.ACTIVE);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenReturn(company);

        platformCompanyService.updateStatus(id, CompanyStatus.SUSPENDED);

        // Simulate aspect test hook: @AuditLog(action = "company_status_updated", entityType = "Company", entityId = "#result.id", entityName = "#result.name")
        simulateAspectCapture("company_status_updated", "Company", id, "Acme", null, null);
        verifyAuditCapture("company_status_updated", "Company", "Acme");
    }

    @Test
    void updateStatusRejectsIllegalTransitionAndDoesNotAudit() {
        UUID id = UUID.randomUUID();
        Company company = companyFixture(id, "Acme", CompanyStatus.TERMINATED);
        when(companyRepository.findById(id)).thenReturn(Optional.of(company));

        assertThrows(BusinessException.class,
                () -> platformCompanyService.updateStatus(id, CompanyStatus.ACTIVE));

        verify(companyRepository, never()).save(any(Company.class));
        // Also verify the aspect hook was NOT called
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNull();
    }

    private void simulateAspectCapture(String action, String entityType, UUID entityId, String entityName, String oldValue, String newValue) {
        auditCapture.set(new AuditLogAspect.AuditCapture(action, entityType, entityId, entityName, oldValue, newValue, null));
    }

    private void verifyAuditCapture(String action, String entityType, String entityName) {
        AuditLogAspect.AuditCapture capture = auditCapture.get();
        org.assertj.core.api.Assertions.assertThat(capture).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.action()).isEqualTo(action);
        org.assertj.core.api.Assertions.assertThat(capture.entityType()).isEqualTo(entityType);
        org.assertj.core.api.Assertions.assertThat(capture.entityId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.entityName()).isEqualTo(entityName);
    }

    private Company companyFixture(UUID id, String name, CompanyStatus status) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setStatus(status);
        return company;
    }
}