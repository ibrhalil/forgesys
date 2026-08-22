package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AppRecordValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRecordValueRepository extends JpaRepository<AppRecordValue, UUID> {

    /** Bulk fetch for record lists — one query for the whole page (no N+1). */
    List<AppRecordValue> findAllByRecordIdIn(Collection<UUID> recordIds);

    List<AppRecordValue> findAllByRecordId(UUID recordId);

    Optional<AppRecordValue> findByRecordIdAndPropertyId(UUID recordId, UUID propertyId);
}
