package com.ibrhalil.forgesys.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import lombok.Getter;
import lombok.Setter;

/**
 * Soft-delete exclusion rides an auto-applied Hibernate filter (EGH item 4): default
 * behavior is identical to the former @SQLRestriction (every load/query/JOIN of a
 * soft-delete entity hides is_deleted = true rows), but a scoped window can disable
 * it for "include deleted" reads (see repository IncludingDeleted fragments).
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(name = SoftDeleteAuditEntity.SOFT_DELETE_FILTER, autoEnabled = true, applyToLoadByKey = true)
@Filter(name = SoftDeleteAuditEntity.SOFT_DELETE_FILTER, condition = "is_deleted = false")
public abstract class SoftDeleteAuditEntity extends AuditEntity {

    public static final String SOFT_DELETE_FILTER = "softDeleteFilter";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
