package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformCompanyService {

    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return executeWithoutTenantContext(() ->
                companyRepository.findAll().stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(UUID id) {
        return executeWithoutTenantContext(() ->
                companyRepository.findById(id)
                        .map(this::mapToResponse)
                        .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id))
        );
    }

    @Transactional
    public CompanyResponse updateStatus(UUID id, CompanyStatus status) {
        Company saved = executeWithoutTenantContext(() -> {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id));

            if (!company.getStatus().canTransitionTo(status)) {
                // [RISK-32] reject illegal transitions (e.g. TERMINATED->ACTIVE,
                // ACTIVE->PROVISIONING) that would leave the tenant in a broken state.
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Illegal company status transition: " + company.getStatus() + " -> " + status);
            }
            company.setStatus(status);
            return companyRepository.save(company);
        });
        log.info("Company status updated: id={}, newStatus={}", saved.getId(), saved.getStatus());
        // Audited after executeWithoutTenantContext restores the caller's tenant context:
        // platform actions operate on the public schema, but t_audit_logs is tenant-scoped,
        // so the record is written to the platform admin's (system) tenant schema.
        auditService.record("company_status_updated", "Company", saved.getId(), saved.getName());
        return mapToResponse(saved);
    }

    /**
     * Executes the given operation with the TenantContext cleared,
     * ensuring that queries hit the public schema without tenant interference.
     * The original context is restored afterward.
     */
    private <T> T executeWithoutTenantContext(Supplier<T> operation) {
        String originalTenant = TenantContext.getCurrentTenant().orElse(null);
        try {
            TenantContext.clear();
            return operation.get();
        } finally {
            if (originalTenant != null) {
                TenantContext.setCurrentTenant(originalTenant);
            }
        }
    }

    private CompanyResponse mapToResponse(Company company) {
        // schemaName is intentionally omitted — internal detail, not part of
        // the API contract (CompanyResponse internal leak cleanup).
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSubdomain(),
                company.getStatus()
        );
    }
}
