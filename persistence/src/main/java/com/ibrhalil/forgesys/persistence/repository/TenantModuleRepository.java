package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.TenantModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    List<TenantModule> findByCompanyId(UUID companyId);

    Optional<TenantModule> findByCompanyIdAndModuleKey(UUID companyId, String moduleKey);
}
