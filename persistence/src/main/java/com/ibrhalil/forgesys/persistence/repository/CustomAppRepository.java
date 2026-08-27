package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.CustomApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomAppRepository extends JpaRepository<CustomApp, UUID>, JpaSpecificationExecutor<CustomApp> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Whether the project holds any custom app — locks the project type while content exists (K-45). */
    boolean existsByProjectId(UUID projectId);
}
