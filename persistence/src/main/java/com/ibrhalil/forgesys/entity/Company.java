package com.ibrhalil.forgesys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"dbRole"})
@Table(
        name = "t_companies",
        schema = "public",
        indexes = {
                @Index(name = "idx_company_subdomain", columnList = "subdomain"),
                @Index(name = "idx_company_email_domain", columnList = "email_domain")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_companies_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_companies_subdomain", columnNames = "subdomain"),
                @UniqueConstraint(name = "uk_companies_email_domain", columnNames = "email_domain"),
                @UniqueConstraint(name = "uk_companies_schema_name", columnNames = "schema_name")
        }
)
@SQLDelete(sql = "UPDATE t_companies SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "subdomain", nullable = false, length = 100)
    private String subdomain;

    @Column(name = "email_domain", nullable = false, length = 150)
    private String emailDomain;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "db_role", length = 100)
    private String dbRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status;
}
