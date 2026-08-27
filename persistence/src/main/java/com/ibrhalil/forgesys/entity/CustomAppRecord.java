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

/** A row of a custom {@link CustomApp} (K-15) — cell values live in {@link CustomAppRecordValue} (JSONB EAV). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(
        name = "t_custom_app_records",
        indexes = {
                @Index(name = "idx_custom_app_records_custom_app", columnList = "custom_app_id")
        }
)
@SQLDelete(sql = "UPDATE t_custom_app_records SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class CustomAppRecord extends BaseEntity {

    @Column(name = "custom_app_id", nullable = false, columnDefinition = "uuid")
    private UUID customAppId;
}
