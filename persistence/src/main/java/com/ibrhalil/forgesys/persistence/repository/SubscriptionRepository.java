package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByCompanyId(UUID companyId);
}
