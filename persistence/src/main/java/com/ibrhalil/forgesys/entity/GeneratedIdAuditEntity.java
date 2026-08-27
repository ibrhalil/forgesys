package com.ibrhalil.forgesys.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@MappedSuperclass
public abstract class GeneratedIdAuditEntity extends AuditEntity implements IdentifiableUuid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Override
    public boolean equals(Object o) {
        return EntityEqualityUtil.entityEquals(this, o, getClass());
    }

    @Override
    public int hashCode() {
        return EntityEqualityUtil.entityHashCode(this);
    }
}
