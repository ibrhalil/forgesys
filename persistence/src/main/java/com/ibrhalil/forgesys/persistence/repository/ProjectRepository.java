package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    boolean existsByName(String name);

    /** By exact live name — the default-container adoption lookup (K-45). */
    Optional<Project> findByName(String name);

    /**
     * Id of the per-type default container ("Genel", K-45) — at most one live row per
     * type ({@code uk_projects_default_type} in PG; {@code @SQLRestriction} hides
     * soft-deleted rows on both H2 and PG). List + first() keeps H2 loose in tests.
     */
    @Query("select p.id from Project p where p.type = :type and p.isDefault = true")
    List<UUID> findDefaultIdsByType(@Param("type") ProjectType type);
}
