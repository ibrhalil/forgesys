package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AppView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppViewRepository extends JpaRepository<AppView, UUID> {

    /** The app's views in stable tab order. */
    List<AppView> findAllByAppIdOrderByPositionAscNameAsc(UUID appId);

    /** Scoped lookup — a view is only reachable through its owning app. */
    Optional<AppView> findByIdAndAppId(UUID id, UUID appId);

    boolean existsByAppIdAndName(UUID appId, String name);

    boolean existsByAppIdAndNameAndIdNot(UUID appId, String name, UUID id);

    /** Highest position among the app's live views (null when it has none) —
     *  create appends at max+1. Soft-delete scope via the entity @SQLRestriction. */
    @Query("select max(v.position) from AppView v where v.appId = :appId")
    Integer findMaxPosition(@Param("appId") UUID appId);
}
