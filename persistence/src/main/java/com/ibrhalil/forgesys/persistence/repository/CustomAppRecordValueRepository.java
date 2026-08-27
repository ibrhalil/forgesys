package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.CustomAppRecordValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomAppRecordValueRepository extends JpaRepository<CustomAppRecordValue, UUID> {

    /** Bulk fetch for record lists — one query for the whole page (no N+1). */
    List<CustomAppRecordValue> findAllByRecordIdIn(Collection<UUID> recordIds);

    List<CustomAppRecordValue> findAllByRecordId(UUID recordId);

    Optional<CustomAppRecordValue> findByRecordIdAndPropertyId(UUID recordId, UUID propertyId);
}
