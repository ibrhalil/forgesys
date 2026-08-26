package com.ibrhalil.forgesys.persistence.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.ibrhalil.forgesys.entity.PlatformUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Public-schema platform identity repository (K-50). */
@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Single-column projection for the platform JWT revocation check (RISK-21 pattern). */
    @Query("select u.tokenInvalidBefore from PlatformUser u where u.id = :userId")
    Optional<OffsetDateTime> findTokenInvalidBefore(@Param("userId") UUID userId);
}
