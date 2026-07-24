package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.TenantVerificationToken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantVerificationTokenRepository extends JpaRepository<TenantVerificationToken, UUID> {

    Optional<TenantVerificationToken> findByToken(String token);

    Optional<TenantVerificationToken> findByCompanyId(UUID companyId);
}
