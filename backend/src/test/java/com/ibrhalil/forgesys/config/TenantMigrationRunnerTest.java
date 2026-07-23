package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.service.TenantMigrationSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantMigrationRunnerTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private TenantMigrationSupport tenantMigrationSupport;

    @InjectMocks
    private TenantMigrationRunner runner;

    @Test
    void migratesEveryTenantSchema() {
        when(companyRepository.findAll()).thenReturn(List.of(company("tenant_a"), company("tenant_b")));

        runner.run(null);

        verify(tenantMigrationSupport).migrateSchema("tenant_a");
        verify(tenantMigrationSupport).migrateSchema("tenant_b");
    }

    @Test
    void skipsMigrationWhenNoTenantsExist() {
        when(companyRepository.findAll()).thenReturn(List.of());

        runner.run(null);

        verifyNoInteractions(tenantMigrationSupport);
    }

    @Test
    void skipsTenantWithBlankSchemaName() {
        when(companyRepository.findAll()).thenReturn(List.of(company("  ")));

        runner.run(null);

        verifyNoInteractions(tenantMigrationSupport);
    }

    @Test
    void continuesOtherTenantsWhenOneFails() {
        when(companyRepository.findAll()).thenReturn(List.of(company("tenant_a"), company("tenant_b")));
        doThrow(new RuntimeException("boom")).when(tenantMigrationSupport).migrateSchema("tenant_a");

        runner.run(null);

        verify(tenantMigrationSupport).migrateSchema("tenant_a");
        verify(tenantMigrationSupport).migrateSchema("tenant_b");
    }

    private Company company(String schemaName) {
        Company company = new Company();
        company.setSchemaName(schemaName);
        return company;
    }
}
