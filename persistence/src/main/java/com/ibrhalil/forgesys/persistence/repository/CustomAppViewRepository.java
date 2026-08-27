package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.CustomAppView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomAppViewRepository extends JpaRepository<CustomAppView, UUID> {

    /** The custom app's views in stable tab order. */
    List<CustomAppView> findAllByCustomAppIdOrderByPositionAscNameAsc(UUID customAppId);

    /** Scoped lookup — a view is only reachable through its owning custom app. */
    Optional<CustomAppView> findByIdAndCustomAppId(UUID id, UUID customAppId);

    boolean existsByCustomAppIdAndName(UUID customAppId, String name);

    boolean existsByCustomAppIdAndNameAndIdNot(UUID customAppId, String name, UUID id);

    /** Highest position among the custom app's live views (null when it has none) —
     *  create appends at max+1. Soft-delete scope via the entity @SQLRestriction. */
    @Query("select max(v.position) from CustomAppView v where v.customAppId = :customAppId")
    Integer findMaxPosition(@Param("customAppId") UUID customAppId);
}
