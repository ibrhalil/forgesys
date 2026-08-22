package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * A row of a custom {@link App} (K-15 / Epic 3.0.B). The row itself carries no data —
 * its cell values live in {@link AppRecordValue} rows (JSONB EAV, K-15). Only record
 * metadata (audit columns) lives here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_app_records",
        indexes = {
                @Index(name = "idx_app_records_app", columnList = "app_id")
        }
)
@SQLDelete(sql = "UPDATE t_app_records SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class AppRecord extends BaseEntity {

    @Column(name = "app_id", nullable = false, columnDefinition = "uuid")
    private UUID appId;
}
