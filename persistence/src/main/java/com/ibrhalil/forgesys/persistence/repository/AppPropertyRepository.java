package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AppProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppPropertyRepository extends JpaRepository<AppProperty, UUID> {

    /** The app's property set in stable column order. */
    List<AppProperty> findAllByAppIdOrderByPositionAscNameAsc(UUID appId);

    /** Scoped lookup — a property is only reachable through its owning app. */
    Optional<AppProperty> findByIdAndAppId(UUID id, UUID appId);

    boolean existsByAppIdAndName(UUID appId, String name);

    boolean existsByAppIdAndNameAndIdNot(UUID appId, String name, UUID id);

    /** Highest position among the app's live properties (null when it has none) —
     *  create appends at max+1. Soft-delete scope via the entity @SQLRestriction. */
    @Query("select max(p.position) from AppProperty p where p.appId = :appId")
    Integer findMaxPosition(@Param("appId") UUID appId);

    /** Hard-deletes the property's value rows — dependent data, no soft-delete (see {@code AppRecordValue}). */
    @Modifying
    @Query("delete from AppRecordValue v where v.propertyId = :propertyId")
    void deleteValuesByPropertyId(@Param("propertyId") UUID propertyId);
}
