package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
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
        return executeWithoutTenantContext(() -> {
            Company company = companyRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id));

            company.setStatus(status);
            Company saved = companyRepository.save(company);
            log.info("Company status updated: id={}, newStatus={}", saved.getId(), saved.getStatus());

            return mapToResponse(saved);
        });
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
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSubdomain(),
                company.getSchemaName(),
                company.getDbRole(),
                company.getStatus()
        );
    }
}
