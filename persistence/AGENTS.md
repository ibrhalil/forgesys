# persistence/AGENTS.md

## Modül

JPA entity'ler, repository'ler, multi-tenancy altyapısı, Flyway migration. `common`'a bağımlı. Spring Web/Security bağımlılığı YOK. Kök AGENTS.md'deki genel kurallar geçerli.

## Komutlar

```bash
./mvnw -pl persistence test                       # modül testleri
./mvnw -pl persistence -am clean install          # bağımlılıklarıyla build (common dahil)
```

## Entity Hiyerarşisi (ZORUNLU — yeni entity buna uymalı)

```
AuditEntity (@MappedSuperclass — created/updated date+by, OffsetDateTime, timestamptz)
  ├─ SoftDeleteAuditEntity (isDeleted, deletedAt, @Version, @SQLRestriction("is_deleted = false"))
  │    └─ BaseEntity (UUID id + equals/hashCode)   ← Company, User, Role, Permission, Group
  │       ├─ UserAccount, UserProfile (@MapsId, extends SoftDeleteAuditEntity)
  └─ GeneratedIdAuditEntity (UUID id, soft delete YOK)  ← RefreshToken, TenantVerificationToken
```

**Kurallar:**
- Tüm ID'ler `UUID` (`@GeneratedValue(strategy = GenerationType.UUID)`, `columnDefinition="uuid"`).
- Tablo adları `t_` prefix'li (`t_users`, `t_roles`, ...). Constraint adları: `idx_*`, `uk_*`, `fk_*`.
- `@SQLDelete` her concrete entity'de ayrı (table-specific SQL, `version = version + 1`).
- `@SQLRestriction("is_deleted = false")` `SoftDeleteAuditEntity`'de → tüm subclass'lara inherit, soft-deleted otomatik filtrelenir.
- `@Version Long version` optimistic locking için (soft-delete entity'lerde).
- Spring Data auditing (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`/`@LastModifiedBy`) → `OffsetDateTime` + `timestamptz`.
- `UserAccount`/`UserProfile` `@MapsId` ile (gereksiz FK yok, shared PK).
- **Soft-delete OLMAYAN entity'ler** `GeneratedIdAuditEntity`'den inherit edilir — yalnızca `created_at`/`updated_at`/`created_by`/`updated_by` alanları olur, `is_deleted`/`version` yoktur. Örnek: `RefreshToken` (doğal olarak kısa ömürlü, revoke edilir, soft-delete gerekmez), `TenantVerificationToken` (tek kullanımlık, `usedAt` ile invalidasyon — soft-delete değil).

## Multi-Tenancy (Schema-per-Tenant)

- **Strateji:** Hibernate `SCHEMA`. Shared connection pool + `SET search_path TO <tenant>, public`.
- `SchemaPerTenantConnectionProvider` (`persistence/tenant/`) — schema adını `^[a-z0-9_]+$` regex ile doğrular (SQL injection savunması), `getConnection`'da `SET search_path`, `releaseConnection`'da reset.
- `TenantIdentifierResolver` — `TenantContext.getCurrentTenant()` okur, null/blank ise `"public"`.
- **Master şema (`public`):** `Company` (name, subdomain, emailDomain, schemaName, dbRole, status) + `TenantVerificationToken` (signup doğrulama token'ları — tenant bağlamı yok, public'te tutulur).
- **Tenant şeması (`tenant_xxx`):** User/Role/Permission/Group + join tabloları. Her tenant kendi verisi.
- **Tenant şema adı:** `tenant_<subdomain>` (lowercase, tireler `_`'e).

## CompanyStatus Kullanımı (K-21, 2026-07-20)

Enum değerleri ve gerçek kullanım amaçları:
- `PROVISIONING` — Signup sonrası `createPendingCompany()` set eder. **Şema/migration YOK**, hafif. Admin email doğrulaması bekleniyor. Doğrulama linki tıklanınca `verifyAndProvision()` `ACTIVE`'e çeker + şema + Flyway + admin user yaratır (senkron).
- `ACTIVE` — Tamamen provisioned tenant. `TenantFilter` subdomain çözümünde sadece bu duruma izin verir.
- `SUSPENDED` / `TERMINATED` — Yönetimsel durumlar (plan ödenmedi, tenant kapatıldı vb.), Faz 6 kapsamında.

> Önceki `provisionTenant` direkt `ACTIVE` set ediyordu (şema + Flyway + admin user hemen). K-21 ile bu iki fazlı oldu: `PROVISIONING` → `ACTIVE`. Backward-compat: mevcut `ACTIVE` tenant'lar etkilenmez.

## Gotcha'lar

- **`ddl-auto=none` ZORUNLU** (ASLA `validate`). Schema-per-tenant + lazy tenant şeması yüzünden `validate` startup'ta tüm entity'leri `public` şemasında doğrulamaya çalışır → `missing table` çökmesi. Şema tamamen Flyway'de.
- **`@EntityScan("com.ibrhalil.systemforge.entity")`** (entity'ler `entity` paketinde, `persistence.entity` DEĞİL). Repository'ler `com.ibrhalil.systemforge.persistence.repository`. Bu split `MultiTenancyJpaConfig`'te (backend) explicit scan ile bağlanır.
- **`hashCode()` bug (DEBT-7, SF-180):** `BaseEntity`/`GeneratedIdAuditEntity` `Objects.hash(getClass())` → aynı tipteki tüm entity'lere aynı hash → `Set<Role>` çakışması. RBAC öncesi düzeltilmeli.
- **Soft-delete + UNIQUE (RISK-17, SF-179):** DB seviyesi UNIQUE soft-delete ile çakışır (silinmiş satır kalır). Partial index gerekli: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Yalnızca `SoftDeleteAuditEntity` subclass'ları için geçerli; `GeneratedIdAuditEntity` subclass'ları (`RefreshToken`, `TenantVerificationToken`) soft-delete olmadığından normal UNIQUE kullanır.

## Flyway Migration

```
src/main/resources/db/migration/
├── public/   # startup'ta auto-config — public şema (t_companies, t_tenant_verification_tokens)
└── tenant/   # provisioning'de programmatik — her tenant şemasında (TenantProvisioningService.verifyAndProvision)
```

- Public migration Spring Boot auto-config ile; tenant migration `TenantProvisioningService.verifyAndProvision()` ile programmatik çalışır (artık `createPendingCompany()`'da DEĞİL — şema verify adımında yaratılır, K-21).
- **Mevcut tenant'ları etkileyen yeni tenant migration'ında `TenantMigrationRunner` gerekir** (SF-178) — yoksa mevcut tenant'lar V1'de takılır.
- H2 uyumu için `TIMESTAMP WITH TIME ZONE` (uzun form) kullan — `TIMESTAMPTZ` shorthand H2'de desteklenmez.
- **TenantVerificationToken tablosu (SF-099, `public/V2__tenant_verification_tokens.sql`):** `public` şemada, çünkü token doğrulaması tenant bağlamı kurulmadan önce yapılır. Token hash olarak saklanır; admin password yine hash'li (BCrypt). `expiresAt`, `usedAt` alanları ile tek-kullanımlık + TTL.

## Repository

Paket `com.ibrhalil.systemforge.persistence.repository`. `JpaRepository` extend eder. Mevcut:
- `CompanyRepository` (`findBySubdomain`, `findByEmailDomain`, `findBySchemaName`)
- `UserRepository` (`findByEmail`, `findByUsername`)
- `RefreshTokenRepository` (`findByToken`)
- `TenantVerificationTokenRepository` (`findByToken`) — **[SF-099]**
