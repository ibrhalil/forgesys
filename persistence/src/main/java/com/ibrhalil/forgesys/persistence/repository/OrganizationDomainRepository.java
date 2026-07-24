package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.OrganizationDomain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationDomainRepository extends JpaRepository<OrganizationDomain, UUID> {

    Optional<OrganizationDomain> findByDomain(String domain);

    List<OrganizationDomain> findByCompanyId(UUID companyId);

    List<OrganizationDomain> findByCompanyIdAndVerifiedTrue(UUID companyId);

    boolean existsByDomain(String domain);
}
