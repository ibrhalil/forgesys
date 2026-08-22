package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.service.TenantMigrationSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

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
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of(view("tenant_a"), view("tenant_b")));

        runner.run(null);

        verify(tenantMigrationSupport).migrateSchema("tenant_a");
        verify(tenantMigrationSupport).migrateSchema("tenant_b");
    }

    @Test
    void skipsMigrationWhenNoTenantsExist() {
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of());

        runner.run(null);

        verifyNoInteractions(tenantMigrationSupport);
    }

    @Test
    void skipsTenantWithBlankSchemaName() {
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of(view("  ")));

        runner.run(null);

        verifyNoInteractions(tenantMigrationSupport);
    }

    @Test
    void continuesOtherTenantsWhenOneFails() {
        when(companyRepository.findAllTenantSchemas()).thenReturn(List.of(view("tenant_a"), view("tenant_b")));
        doThrow(new RuntimeException("boom")).when(tenantMigrationSupport).migrateSchema("tenant_a");

        runner.run(null);

        verify(tenantMigrationSupport).migrateSchema("tenant_a");
        verify(tenantMigrationSupport).migrateSchema("tenant_b");
    }

    private record TenantView(UUID id, String schemaName, CompanyStatus status)
            implements CompanyRepository.TenantSchemaView {
        @Override public UUID getId() { return id; }
        @Override public String getSchemaName() { return schemaName; }
        @Override public CompanyStatus getStatus() { return status; }
    }

    private CompanyRepository.TenantSchemaView view(String schemaName) {
        return new TenantView(UUID.randomUUID(), schemaName, CompanyStatus.ACTIVE);
    }
}
