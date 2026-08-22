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
 * One cell of the EAV model (K-15 / Epic 3.0.B): the {@link PropertyType}-validated
 * value of one {@link AppProperty} on one {@link AppRecord}, stored as raw JSON text in
 * a {@code jsonb} column (mapped as plain {@code String} + {@code columnDefinition} —
 * the established codebase JSONB convention from {@link AuditLog}, with
 * {@code stringtype=unspecified} handling the PG bind).
 *
 * <p><em>No soft delete</em> ({@link GeneratedIdAuditEntity}): value rows are dependent
 * data — clearing a value removes the row, re-setting inserts it again (plain UNIQUE
 * {@code (record_id, property_id)} instead of a partial index). A JSON {@code null}
 * value is not stored; absence of a row = empty cell.
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
     * Raw JSON text (type-validated by the service before persisting). The backticks
     * force identifier quoting — {@code value} is a reserved word on H2 (test profile
     * DDL) and quoting keeps PostgreSQL consistent (unquoted folds to lowercase there,
     * the migration column is lowercase too).
     */
    @Column(name = "`value`", columnDefinition = "jsonb")
    private String value;
}
