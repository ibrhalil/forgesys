package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByKey(String key);
}
