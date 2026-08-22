package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findBySubdomain(String subdomain);

    Optional<Company> findBySchemaName(String schemaName);

    /**
     * Lightweight tenant listing for the startup runners (K-40) — no entity
     * hydration, cost does not grow with entity graph size.
     */
    @Query("select c.id as id, c.schemaName as schemaName, c.status as status from Company c")
    List<TenantSchemaView> findAllTenantSchemas();

    /** Startup projection (K-40): just enough to iterate tenant schemas. */
    interface TenantSchemaView {
        UUID getId();
        String getSchemaName();
        CompanyStatus getStatus();
    }
}
