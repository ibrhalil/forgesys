package com.ibrhalil.forgesys.entity;

import java.util.UUID;

/** Implemented by the id-bearing mapped superclasses (BaseEntity, GeneratedIdAuditEntity). */
public interface IdentifiableUuid {

    UUID getId();
}
