package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Company_;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformCompanyService {

    /**
     * Filterable/sortable attributes of the platform company list (K-49 — the list is
     * now paged and engine-wired); {@code q} matches {@code name} and
     * {@code subdomain}. {@code schemaName} stays deliberately unregistered (internal
     * detail, same posture as {@link CompanyResponse}).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Company_.NAME, FilterFieldType.STRING, true)
            .field(Company_.SUBDOMAIN, FilterFieldType.STRING, true)
            .enumField(Company_.STATUS, CompanyStatus.class, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final CompanyRepository companyRepository;
    private final PlatformCompanyListQueryExecutor platformCompanyListQueryExecutor;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(String q, List<String> qFields, Pageable pageable) {
        return doSearch(StringUtils.hasText(q) ? q.trim() : null, qFields, List.of(), pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /platform/companies/search}. */
    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<CompanyResponse> doSearch(String q, List<String> qFields,
            List<com.ibrhalil.forgesys.dto.FilterCriteria> filters, Pageable pageable) {
        return executeWithoutTenantContext(() -> {
            Specification<Company> spec = FilterSpecifications.from(FILTER_FIELDS, q, qFields, filters);
            return platformCompanyListQueryExecutor.search(spec, pageable);
        });
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
    @AuditLog(action = "company_status_updated", entityType = "Company", entityId = "#result.id", entityName = "#result.name")
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
