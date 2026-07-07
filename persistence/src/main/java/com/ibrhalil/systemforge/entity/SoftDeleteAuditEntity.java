package com.ibrhalil.systemforge.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.hibernate.annotations.SQLRestriction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
@SQLRestriction("is_deleted = false")
public abstract class SoftDeleteAuditEntity extends AuditEntity {

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
