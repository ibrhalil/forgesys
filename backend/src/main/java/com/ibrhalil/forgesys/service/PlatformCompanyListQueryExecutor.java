package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.Company_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Platform company list read side (K-49) — replaced the unpaged {@code findAll()} (the
 * last K-37 paging violation). Runs INSIDE {@code executeWithoutTenantContext}: the
 * cleared TenantContext pins the EntityManager to the public schema.
 */
@Component
public class PlatformCompanyListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<CompanyResponse> search(@Nullable Specification<Company> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, Company.class, CompanyResponse.class,
                PlatformCompanyService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(CompanyResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(Company_.NAME),
                        root.get(Company_.SUBDOMAIN),
                        root.get(Company_.STATUS)),
                spec, pageable);
    }
}
