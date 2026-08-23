package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.App;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AppRepository extends JpaRepository<App, UUID>, JpaSpecificationExecutor<App> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** Whether the project holds any app — locks the project type while content exists (K-45). */
    boolean existsByProjectId(UUID projectId);
}
