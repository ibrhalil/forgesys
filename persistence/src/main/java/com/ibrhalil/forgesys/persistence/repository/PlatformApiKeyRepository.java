package com.ibrhalil.forgesys.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ibrhalil.forgesys.entity.PlatformApiKey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Public-schema API key repository (K-50) — prefix lookup feeds the X-API-Key filter. */
@Repository
public interface PlatformApiKeyRepository extends JpaRepository<PlatformApiKey, UUID> {

    Optional<PlatformApiKey> findByKeyPrefix(String keyPrefix);

    List<PlatformApiKey> findByPlatformUserIdOrderByCreatedDateDesc(UUID platformUserId);
}
