package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.CustomAppProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomAppPropertyRepository extends JpaRepository<CustomAppProperty, UUID> {

    /** The custom app's property set in stable column order. */
    List<CustomAppProperty> findAllByCustomAppIdOrderByPositionAscNameAsc(UUID customAppId);

    /** Scoped lookup — a property is only reachable through its owning custom app. */
    Optional<CustomAppProperty> findByIdAndCustomAppId(UUID id, UUID customAppId);

    boolean existsByCustomAppIdAndName(UUID customAppId, String name);

    boolean existsByCustomAppIdAndNameAndIdNot(UUID customAppId, String name, UUID id);

    /** Highest position among the custom app's live properties (null when it has none) —
     *  create appends at max+1. Soft-delete scope via the entity @SQLRestriction. */
    @Query("select max(p.position) from CustomAppProperty p where p.customAppId = :customAppId")
    Integer findMaxPosition(@Param("customAppId") UUID customAppId);

    /** Hard-deletes the property's value rows — dependent data, no soft-delete (see {@code CustomAppRecordValue}). */
    @Modifying
    @Query("delete from CustomAppRecordValue v where v.propertyId = :propertyId")
    void deleteValuesByPropertyId(@Param("propertyId") UUID propertyId);
}
