package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.util.UUID;

/**
 * One EAV cell (K-15): raw JSON in a {@code jsonb} column (String mapping, the
 * {@link AuditLog} convention; relies on {@code stringtype=unspecified}). No soft
 * delete — clear = row delete, re-set = insert (plain UNIQUE
 * {@code (record_id, property_id)}); a missing row means an empty cell.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "value")
@Table(
        name = "t_app_record_values",
        indexes = {
                @Index(name = "idx_app_record_values_record", columnList = "record_id"),
                @Index(name = "idx_app_record_values_property", columnList = "property_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class AppRecordValue extends GeneratedIdAuditEntity {

    @Column(name = "record_id", nullable = false, columnDefinition = "uuid")
    private UUID recordId;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    /**
     * Raw JSON text, type-validated by the service. Backticks force identifier
     * quoting — {@code value} is a reserved word on H2; quoting stays consistent
     * with PG's lowercase folding.
     */
    @Column(name = "`value`", columnDefinition = "jsonb")
    private String value;
}
