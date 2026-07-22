# persistence/AGENTS.md

## Modül

JPA entity'ler, repository'ler, multi-tenancy altyapısı, Flyway migration. `common`'a bağımlı. Spring Web/Security bağımlılığı YOK. Kök AGENTS.md'deki genel kurallar geçerli.

Komutlar için bkz. [README](../README.md#build-komutlari). Modül özet: `./mvnw -pl persistence test`, `./mvnw -pl persistence -am clean install`.

## Entity Hiyerarşisi

Detay (devralma ağacı + şema-tablo mapping) tek source: [ARCHITECTURE.md - Entity Hiyerarşisi](../ARCHITECTURE.md#entity-hiyerarşisi). Özet:

```
AuditEntity (@MappedSuperclass — createdDate/updatedDate+by, OffsetDateTime, timestamptz)
  ├ SoftDeleteAuditEntity (isDeleted, deletedAt, @Version, @SQLRestriction("is_deleted = false"))
  │    └ BaseEntity (UUID id + equals/hashCode)   <- Company, User, Role, Permission, Group
  │       ├ UserAccount, UserProfile (@MapsId, extends SoftDeleteAuditEntity)
  └ GeneratedIdAuditEntity (UUID id, soft delete YOK)  <- RefreshToken
```

> Java alanı `createdDate` (kolon `created_at`). `TenantVerificationToken` entity'si HENÜZ YOK — K-21 (Epic 2.0.C) ile gelecek, uygulanmadı.

Kurallar:
- Tüm ID'ler `UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`, `columnDefinition="uuid"`).
- Tablo adları `t_` prefix'li (`t_users`, `t_roles`, ...). Constraint adları: `idx_*`, `uk_*`, `fk_*`.
- `@SQLDelete` her concrete entity'de ayrı (table-specific SQL, `version = version + 1`).
- `@SQLRestriction("is_deleted = false")` `SoftDeleteAuditEntity`'de -> tüm subclass'lara inherit.
- Spring Data auditing (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy`) -> `OffsetDateTime` + `timestamptz`.
- **Soft-delete OLMAYAN entity'ler** `GeneratedIdAuditEntity`'den inherit edilir (yalnızca auditing alanları, `is_deleted`/`version` yok). Örnek: `RefreshToken` (kısa ömürlü, revoke edilir).

## Multi-Tenancy (Schema-per-Tenant)

Strateji, request lifecycle ve şema-tablo mapping detayı tek source: [ARCHITECTURE.md](../ARCHITECTURE.md#schema-per-tenant-modeli). Persistence'a özgü:

- `SchemaPerTenantConnectionProvider` (`persistence/tenant/`) — schema adını `^[a-z0-9_]+$` regex ile doğrular (SQL injection savunması), `getConnection`'da `SET search_path`, `releaseConnection`'da reset.
- `TenantIdentifierResolver` — `TenantContext.getCurrentTenant()` okur, null/blank ise `"public"`.
- **Master şema (`public`):** `Company` (name, subdomain, emailDomain, schemaName, dbRole, status). `TenantVerificationToken` K-21 sonrası buraya gelir (henüz YOK).
- **Tenant şeması (`tenant_xxx`):** User/Role/Permission/Group + join tabloları. Her tenant kendi verisi.
- **Tenant şema adı:** `tenant_<subdomain>` (lowercase, tireler `_`'e).
- **CompanyStatus** enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. **Mevcut kod yalnız `ACTIVE` kullanır** (`provisionTenant` direkt ACTIVE set eder, tek fazlı senkron). `PROVISIONING`/`SUSPENDED`/`TERMINATED` ileriki fazlar için.

## Flyway Migration

```
src/main/resources/db/migration/
├ public/   # startup'ta auto-config — public şema (t_companies)
└ tenant/   # provisioning'de programmatik — her tenant şemasında (TenantProvisioningService.provisionTenant)
```

- Public migration Spring Boot auto-config ile; tenant migration `TenantProvisioningService.provisionTenant()` ile programmatik çalışır.
- **Mevcut tenant'ları etkileyen yeni tenant migration'ında `TenantMigrationRunner` gerekir** ([RISK-16](../docs/DECISIONS.md#risk-16--yeni-tenant-migration-mevcut-tenantlarda-calismaz)) — yoksa mevcut tenant'lar V1'de takılır.
- H2 uyumu için `TIMESTAMP WITH TIME ZONE` (uzun form) kullan — `TIMESTAMPTZ` shorthand H2'de desteklenmez.

## Repository

Paket `com.ibrhalil.systemforge.persistence.repository`. `JpaRepository` extend eder. Mevcut:
- `CompanyRepository` (`findBySubdomain`, `findByEmailDomain`, `findBySchemaName`)
- `UserRepository` (`findByEmail`, `findByUsername`)
- `RefreshTokenRepository` (`findByToken`)

> `TenantVerificationTokenRepository` K-21 (Epic 2.0.C) ile gelecek — henüz YOK.

## Gotcha'lar

- **`ddl-auto=none` ZORUNLU** (ASLA `validate`). Schema-per-tenant + lazy tenant şeması yüzünden `validate` startup'ta tüm entity'leri `public` şemasında doğrulamaya çalışır -> `missing table` çökmesi. Şema tamamen Flyway'de. (Test profili istisna: `create-drop` + `flyway.enabled=false`.)
- **`@EntityScan("com.ibrhalil.systemforge.entity")`** (entity'ler `entity` paketinde, `persistence.entity` DEĞİL). Repository'ler `com.ibrhalil.systemforge.persistence.repository`. Bu split `MultiTenancyJpaConfig`'te (backend) explicit scan ile bağlanır.
- **`hashCode()` bug ([DEBT-7](../docs/DECISIONS.md#debt-7--hashcode-bug)):** `BaseEntity`/`GeneratedIdAuditEntity` `Objects.hash(getClass())` -> aynı tipteki tüm entity'lere aynı hash -> `Set<Role>` çakışması. RBAC öncesi düzeltilmeli.
- **Soft-delete + UNIQUE ([RISK-17](../docs/DECISIONS.md#risk-17--soft-delete--unique-cakismasi)):** DB seviyesi UNIQUE soft-delete ile çakışır (silinmiş satır kalır). Partial index gerekli: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Yalnızca `SoftDeleteAuditEntity` subclass'ları için; `GeneratedIdAuditEntity` subclass'ları (`RefreshToken`) normal UNIQUE kullanır.
