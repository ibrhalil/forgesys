# Mimari

ForgeSys — modüler çok-kiracılı (multi-tenant) SaaS platformu. Schema-per-tenant izolasyonu, UUID PK, soft-delete + optimistic locking, Spring Data auditing. Hibrit ürün modeli: built-in modüller (pm: Projects & Tasks, apps: App Builder, notes — üçü aktif; Warehouse/Logistics planlı) + tenant custom app'leri (Notion-style App Builder, `apps` modülü). Bu doküman **mevcut sistemi** belgeler: auth (K-34), RBAC (K-26), audit/login/request log (K-19/K-27), plan/modül sistemi (K-16), app builder (K-15/K-42) ve metrics expose (K-43) uygulanmış durumdadır; kalan işler [`ROADMAP.md`](ROADMAP.md)'de.

## Sistem Bileşenleri

```mermaid
flowchart LR
    subgraph Browser
        UI[React SPA<br/>:3000]
    end
    subgraph DevHost[Dev Host]
        Vite[Vite Dev Server<br/>/api, /actuator proxy]
    end
    subgraph AppHost[Application Host]
        SB[Spring Boot<br/>:8080]
        TF["TenantFilter<br/>subdomain → schema"]
        CTX[(TenantContext<br/>ThreadLocal)]
        SB --- TF
        TF --- CTX
    end
    subgraph DataHost[Data Layer]
        PG[(PostgreSQL 16<br/>public + tenant_* schemas)]
        Redis[(Redis 7.4<br/>refresh token store + rotation<br/>jti blacklist + rate limit)]
    end

    UI --> Vite
    Vite -- proxy --> SB
    SB --> PG
    SB --> Redis
```

**Dev:** Browser → Vite (`:3000`) → backend (`:8080`). **Prod:** Nginx gateway planlandı (K-33 — erteli), şu an Docker compose içinde tek app container. CI (GitHub Actions) develop/main push'unda GHCR imajı basar (`:latest`/`:edge`); sunucuya deploy manuel. Prod'da management endpoint'leri ayrı portta (8081, internal-only — [K-43](DECISIONS.md#k-43)).

## HTTP Request Yaşam Döngüsü

Tenant bağlamlı bir isteğin (`/api/v1/...`) sistemden geçiş akışı. `/api/v1/auth/**` ve `/actuator/**` yolları `TenantFilter.shouldNotFilter()` ile muaf tutulur — tenant bağlamı kurmaz.

```mermaid
sequenceDiagram
    participant B as Browser
    participant TF as TenantFilter
    participant CTX as TenantContext<br/>(ThreadLocal)
    participant DS as DispatcherServlet
    participant SVC as Service<br/>@Transactional
    participant H as Hibernate Session
    participant RES as TenantIdentifierResolver
    participant CP as SchemaPerTenantConnectionProvider
    participant DB as PostgreSQL

    B->>TF: GET /api/v1/users<br/>Host: acme.forgesys.app
    TF->>TF: extractSubdomain(Host) → "acme"
    TF->>DB: CompanyRepository.findBySubdomain("acme")
    DB-->>TF: Company{schemaName: "tenant_acme"}
    TF->>CTX: setCurrentTenant("tenant_acme")
    TF->>DS: chain.doFilter()
    DS->>SVC: invoke()
    SVC->>H: open session
    H->>RES: resolveTenantIdentifier()
    RES->>CTX: getCurrentTenant()
    CTX-->>RES: "tenant_acme"
    RES-->>H: "tenant_acme"
    H->>CP: getConnection("tenant_acme")
    CP->>CP: validate ^[a-z0-9_]+$
    CP->>DB: SET search_path TO tenant_acme, public
    CP-->>H: Connection
    H->>DB: SELECT * FROM t_users ...
    DB-->>H: rows
    H-->>SVC: entities
    SVC-->>DS: response
    DS-->>TF: write response
    TF->>CTX: clear() in finally
    TF-->>B: 200 OK + JSON
```

**Kritik noktalar:**

- `TenantContext` bir `ThreadLocal<String>` — request thread boyunca yaşar, `finally` bloğunda **mutlaka** clear edilmeli (thread pool reuse nedeniyle).
- `SchemaPerTenantConnectionProvider.getConnection` her connection alımında `SET search_path TO <tenant>, public` çalıştırır; `releaseConnection`'da reset eder.
- Schema adı regex `^[a-z0-9_]+$` ile doğrulanır — SQL injection savunması.
- `@Async` thread'lerde `TenantContext` otomatik taşınmaz — `TaskDecorator` gerekir ([RISK-10](DECISIONS.md#risk-10)).
- Tenant context null ise resolver `"public"` döner → public şema verisine (Company) erişilir.

## Schema-per-Tenant Modeli

Tek PostgreSQL cluster, tek connection pool. Her tenant fiziksel olarak ayrı bir şemada (`tenant_<subdomain>`). Hibrid yaklaşım: schema isolation (data separation) + shared connection pool (operational simplicity).

```mermaid
flowchart TB
    subgraph PG[PostgreSQL]
        subgraph Public[public schema]
            TC[t_companies]
        end
        subgraph T1[tenant_acme]
            TU1[t_users]
            TR1[t_roles]
            TP1[t_permissions]
            TG1[t_groups]
            TJoin1[t_user_roles<br/>t_user_groups<br/>t_role_permissions<br/>t_group_roles<br/>t_role_parents]
            TAcc1[t_user_accounts<br/>t_user_profiles]
            TMod1[t_projects<br/>t_tasks<br/>t_audit_logs<br/>t_login_history<br/>t_request_logs]
            TApps1[t_apps . t_app_properties<br/>t_app_records . t_app_record_values<br/>t_app_views]
            TNotes1[t_notes<br/>t_note_categories]
        end
        subgraph T2[tenant_stark]
            TU2[t_users]
            TR2[t_roles]
            TP2[t_permissions]
            TG2[t_groups]
            TJoin2[t_user_roles<br/>t_user_groups<br/>t_role_permissions<br/>t_group_roles<br/>t_role_parents]
            TAcc2[t_user_accounts<br/>t_user_profiles]
            TMod2[t_projects<br/>t_tasks<br/>t_audit_logs<br/>t_login_history<br/>t_request_logs]
            TApps2[t_apps . t_app_properties<br/>t_app_records . t_app_record_values<br/>t_app_views]
            TNotes2[t_notes<br/>t_note_categories]
        end
    end
```

**Neden SCHEMA stratejisi (DISCRIMINATOR veya DATABASE değil)?**

- **SCHEMA:** Strong isolation + moderate cost. Backup/restore tenant bazında mümkün. `SET search_path` ile runtime switching — tek connection pool yeterli.
- **DISCRIMINATOR_COLUMN:** En düşük maliyet ama en zayıf isolation — tek `WHERE tenant_id = ?`'in unutulması tüm kiracıların verisini sızdırır. Bu platform için kabul edilemez.
- **DATABASE:** En güçlü isolation ama operational maliyet yüksek — per-tenant connection pool, migration koordinasyonu, backup zinciri.

### Şema → Tablo Mapping

| Şema             | Tablo               | Amaç                                 | Migration           |
|------------------|---------------------|--------------------------------------|---------------------|
| `public`         | `t_companies`       | Tenant kayıt (subdomain→schema map, status)  | `public/V1__tenant_registry.sql` |
| `public`         | `t_tenant_verification_tokens` | K-21 signup token'ları (SHA-256 digest; `admin_password_hash` provisioning sonrası null — RISK-30) | `public/V1.1` + `public/V3__token_hash_at_rest.sql` |
| `public`         | `t_plans`           | Plan kataloğu (FREE/PRO/ENTERPRISE; `PlanSyncRunner` upsert — registry kodda, [K-16](DECISIONS.md#k-16)) | `public/V2__plans_subscriptions_modules.sql` |
| `public`         | `t_subscriptions`   | Tenant→plan aboneliği (FREE default) | `public/V2__plans_subscriptions_modules.sql` |
| `public`         | `t_tenant_modules`  | Tenant modül aktivasyon kayıtları   | `public/V2__plans_subscriptions_modules.sql` |
| `tenant_<sub>`   | `t_users`           | Kullanıcı hesabı (credential'lar)    | `tenant/V1__iam_users.sql` |
| `tenant_<sub>`   | `t_auth_tokens`     | Kullanıcı lifecycle token'ları (email verify / password reset — SHA-256 digest, single-use; [K-48](DECISIONS.md#k-48)) | `tenant/V4__user_auth_tokens.sql` |
| `tenant_<sub>`   | `t_user_accounts`   | Security state (lock, failed login)  | `tenant/V1__iam_users.sql` |
| `tenant_<sub>`   | `t_user_profiles`   | PII (isim, telefon, adres)           | `tenant/V1__iam_users.sql` |
| `tenant_<sub>`   | `t_roles`           | RBAC rolleri (+ `all_permissions` flag, parent inheritance) | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_permissions`     | RBAC yetkileri                       | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_groups`          | Kullanıcı grupları                   | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_user_roles`      | User↔Role join                       | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_user_groups`     | User↔Group join                      | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_role_permissions`| Role↔Permission join                 | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_group_roles`     | Group↔Role join                      | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_role_parents`    | Role→parent-role inheritance join    | `tenant/V1.1__iam_rbac.sql` |
| `tenant_<sub>`   | `t_projects`        | Tipli proje konteyneri (K-45: `project_type TASKS/NOTES/APPS` + `parent_project_id` + tip-bazlı default; isim benzersizliği tip bazlı) | `tenant/V1.3` + `tenant/V3__project_container.sql` |
| `tenant_<sub>`   | `t_tasks`           | Task içeriği (project-scoped)        | `tenant/V1.3__pm_projects_tasks.sql` |
| `tenant_<sub>`   | `t_audit_logs`      | Audit trail (append-only, K-19)      | `tenant/V1.2__audit.sql` |
| `tenant_<sub>`   | `t_login_history`   | Login denemeleri (append-only, K-19) | `tenant/V1.2__audit.sql` |
| `tenant_<sub>`   | `t_request_logs`    | Request/trace log + high-risk maskeli body (K-19 katman 3 + K-27) | `tenant/V2__request_logs.sql` |
| `tenant_<sub>`   | `t_apps` + 4        | App builder ailesi: `t_apps`(`project_id` — APPS koleksiyon konteynerine çapalı, K-45) + `t_app_properties(config jsonb)`, `t_app_records`, `t_app_record_values(value jsonb, GIN)`, `t_app_views(config jsonb)` — `apps` modülü aktivasyonda düşer | `module/apps/V1__app_builder.sql` + `module/apps/V2__apps_project_scoping.sql` (per-module history `flyway_schema_history_mod_apps`, [K-15](DECISIONS.md#k-15)) |
| `tenant_<sub>`   | `t_notes`, `t_note_categories` | Notes modülü (markdown; ikisi de `project_id` ile NOTES konteynerine çapalı — K-45; kategori FK `ON DELETE SET NULL`) — `notes` modülü aktivasyonda düşer | `module/notes/V1__notes.sql` + `module/notes/V2__notes_project_scoping.sql` (per-module history `flyway_schema_history_mod_notes`, [K-44](DECISIONS.md#k-44)) |

> Refresh token'lar tabloda DEĞİL — Redis-first (K-34, [DECISIONS](DECISIONS.md#k-34)); eski `t_refresh_tokens` ölü tablosu K-36 temizliğinde kaldırıldı. Migration'ların tamamı pre-1.0.0 squash'ı ile alan-bazlı `V1.x` baseline ailesine indirildi ([K-36](DECISIONS.md#k-36)) — yeni migration'lar `V2`'den devam eder.

**Tenant provisioning akışı** (`TenantProvisioningService`, K-21 iki-fazlı):

**Faz 1 — `createPendingCompany`** (`@Transactional`, hafif):
1. `validateUnique` (subdomain + schemaName; `email_domain` K-32 ile kaldırıldı).
2. `public.t_companies` satırı INSERT (status=`PROVISIONING`).
3. `public.t_tenant_verification_tokens` INSERT (token SHA-256 digest — RISK-30 hash-at-rest; admin email/password-hash/first/last name + expiresAt).
4. `MailSender.send(MailMessage)` ([K-48](DECISIONS.md#k-48)) — TENANT_VERIFY şablonu, link: `${appBaseUrl}/verify-tenant?token=...`.

**Faz 2 — `verifyAndProvision(token)`** (`@Transactional`, senkron ağır — kullanıcı linki tıklar):
1. Token valid mi? (`usedAt != null` → `TENANT_TOKEN_ALREADY_USED`, `expiresAt <= now` → `TENANT_TOKEN_EXPIRED`, yok → `TENANT_TOKEN_INVALID`). Company `PROVISIONING` değilse reject.
2. `CREATE SCHEMA IF NOT EXISTS tenant_<subdomain>` (raw JDBC — PostgreSQL implicit commit, transaction dışına kaçar; DEBT-10 partial).
3. Flyway programmatik: `db/migration/tenant/*.sql` yeni şemada migrate.
4. `TenantContext.setCurrentTenant("tenant_<subdomain>")` set et.
5. Admin user INSERT (email/password-hash token'dan, `emailVerified=true`) + `RbacSeeder.seedForCurrentTenant()` (Admin rolü + permission catalog); token'ın `adminPasswordHash`'i null'lanır (RISK-30 — rollback'te null da döner, DEBT-10 recovery bozulmaz).
6. `TenantContext.clear()` `finally`'de.
7. `Company.status = ACTIVE`, `token.usedAt = now`.
8. Transaction commit'inden sonra (afterCommit senkronizasyonu) sample data seeding ([K-47](DECISIONS.md#k-47)): `TenantSampleDataService` config gate (`forgesys.provisioning.sample-data.enabled`) + iki katman fail-safe; seed'in REQUIRES_NEW tx'i aktivasyon/subscription satırlarını görmek zorunda olduğundan bilinçli post-commit.

**Bootstrap auto-verify** (`provisionSystemTenant`, K-24): `SystemAdminBootstrapRunner` faz 1 (mail yok) + faz 2'yi arka arkaya çağırır — `system` tenant'ı startup'ta mail loop olmadan provision edilir.

> **DEBT-10 (kısmen çözüldü):** `createPendingCompany` tam transactional (yalnız DB write). `verifyAndProvision` `@Transactional` işaretli ama `CREATE SCHEMA` implicit commit → DDL transaction dışına kaçar. Recovery idempotency ile (`IF NOT EXISTS`, `usedAt` guard). Tam transactional DDL PostgreSQL'de mümkün değil.

## Modül Bağımlılık Grafiği

```mermaid
flowchart LR
    Common[common<br/>TenantContext<br/>shared exceptions]
    Persistence[persistence<br/>JPA entities<br/>multi-tenancy infra<br/>Flyway]
    Backend[backend<br/>Spring Boot app<br/>executable jar]
    Frontend[frontend<br/>React SPA<br/>independent]

    Common --> Persistence
    Persistence --> Backend
    Frontend -. "npm build (Maven)" .-> Backend

    style Common fill:#fef3c7
    style Persistence fill:#dbeafe
    style Backend fill:#dcfce7
    style Frontend fill:#fce7f3
```

- **`common`** — Spring/JPA YOK. Yalnız `TenantContext` + paylaşılan exception'lar.
- **`persistence`** — JPA entity'ler, repository'ler, Hibernate multi-tenancy altyapısı, Flyway migration. `common`'a bağımlı.
- **`backend`** — Spring Boot uygulaması (controller/service/security/config). `common` + `persistence`'a bağımlı. **Tek executable jar üretir.**
- **`frontend`** — React SPA. Maven build backend jar'ına static gömülür; bağımsız npm script ile de çalışır.

Döngüsel bağımlılık YASAK. Kök pom yalnız aggregator + version management (lightweight parent); modüllere bağımlılık dayatmaz.

## Entity Hiyerarşisi

```mermaid
classDiagram
    class AuditEntity {
        <<MappedSuperclass>>
        +OffsetDateTime createdDate
        +OffsetDateTime updatedDate
        +String createdBy
        +String updatedBy
    }
    class SoftDeleteAuditEntity {
        <<MappedSuperclass>>
        +boolean isDeleted
        +OffsetDateTime deletedAt
        +Long version
    }
    class BaseEntity {
        +UUID id
    }
    class GeneratedIdAuditEntity {
        +UUID id
    }

    AuditEntity <|-- SoftDeleteAuditEntity
    AuditEntity <|-- GeneratedIdAuditEntity
    SoftDeleteAuditEntity <|-- BaseEntity

    class Company
    class User
    class UserAccount {
        @MapsId
    }
    class UserProfile {
        @MapsId
    }
    class Role
    class Permission
    class Group
    class TenantVerificationToken
    class Plan
    class Subscription
    class TenantModule
    class Project
    class Task
    class AuditLog
    class LoginHistory
    class RequestLog
    class App
    class AppProperty
    class AppRecord
    class AppRecordValue
    class AppView
    class Note
    class NoteCategory

    BaseEntity <|-- Company
    BaseEntity <|-- User
    BaseEntity <|-- Role
    BaseEntity <|-- Permission
    BaseEntity <|-- Group
    BaseEntity <|-- Project
    BaseEntity <|-- Task
    BaseEntity <|-- App
    BaseEntity <|-- AppProperty
    BaseEntity <|-- AppRecord
    BaseEntity <|-- AppView
    BaseEntity <|-- Note
    BaseEntity <|-- NoteCategory
    SoftDeleteAuditEntity <|-- UserAccount
    SoftDeleteAuditEntity <|-- UserProfile
    GeneratedIdAuditEntity <|-- TenantVerificationToken
    GeneratedIdAuditEntity <|-- UserAuthToken
    GeneratedIdAuditEntity <|-- Plan
    GeneratedIdAuditEntity <|-- AuditLog
    GeneratedIdAuditEntity <|-- LoginHistory
    GeneratedIdAuditEntity <|-- RequestLog
    GeneratedIdAuditEntity <|-- AppRecordValue
```

- **`@MappedSuperclass`** — DB tablosu karşılığı yok, sadece alanları concrete entity'lere inherits.
- `@SQLRestriction("is_deleted = false")` `SoftDeleteAuditEntity`'de → tüm subclass'larda soft-deleted satırlar otomatik filtrelenir.
- `@SQLDelete` her concrete entity'de ayrı (table-specific `UPDATE ... SET is_deleted = true, version = version + 1`).
- `UserAccount`/`UserProfile` `@MapsId` ile `User`'a shared PK (gereksiz FK yok).
- Tüm ID'ler UUID (`GenerationType.UUID`). Tablo adları `t_` prefix'li. Constraint'ler `idx_*`, `uk_*`, `fk_*`.
- `Subscription`/`TenantModule` (public şema) `BaseEntity`. Read model'ler hiyerarşi dışındadır ve K-49 ile entity değil kod içi Criteria DTO projection'dır (`web/projection/ProjectionListQuery` — eski `@Immutable @Subselect` `UserDirectoryView` kaldırıldı; view entity yalnızca karmaşık/yoğun tablolar için istisna). `AppRecordValue` ve `RequestLog` soft-delete'siz (`GeneratedIdAuditEntity`) — value clear = satır silinir (K-15); request log append-only (K-27).
- **Tipli proje konteyneri (K-45):** `Project` = typed container (`project_type` NOT NULL: TASKS/NOTES/APPS; katalog aktif modüllerden türer). İçerik çapaları düz UUID kolonlarıdır (`@ManyToOne` yok — Task konvansiyonu): `Task.projectId`, `Note.projectId` + `NoteCategory.projectId` (NOTES), `App.projectId` (APPS koleksiyonu). İlişkisel veri katmanı (t_links) bilinçli erteli — talep-kapılı.

> Detaylar: [`persistence/AGENTS.md`](../persistence/AGENTS.md)

## Konfigürasyon Profilleri

Profile-based config. Aktif profil `SPRING_PROFILES_ACTIVE` (default: `dev`).

| Profil | Veritabanı            | Kullanım                       | `.env` gerekli mi |
|--------|-----------------------|--------------------------------|-------------------|
| `dev`  | PostgreSQL `:5432`    | IDE debug (default'lar gömülü) | Hayır             |
| `prod` | PostgreSQL (`.env`)   | `docker-compose-prod.yml`      | **Evet**          |
| `test` | H2 in-memory          | `@SpringBootTest`              | Hayır             |

- `ddl-auto=none` ZORUNLU (ASLA `validate` — schema-per-tenant + lazy tenant şeması startup'ta çöker). Şema tamamen Flyway'de.
- **Test profili istisnası:** `ddl-auto=create-drop` + `flyway.enabled=false`. Spring Boot 4.1 + Flyway 12'de `FlywayAutoConfiguration` H2 dialect algılamıyor (`flyway-database-h2` BOM'da yok), Flyway bean oluşturulmuyor. Test'ler Hibernate'in entity metadata'dan şema üretmesine güveniyor. Gerçek Flyway test'i [`ROADMAP.md`](ROADMAP.md) Testcontainers kapsamında (Faz 3.X).
- H2 `MODE=PostgreSQL`'de çalışır → build Docker gerektirmez.
- H2 sınırları: `JSONB`, partial index, `SET search_path` desteklenmez → multi-tenancy akışı H2'de test edilemez, sadece context yükü doğrulanır.
- **Mail/SMTP ([K-48](DECISIONS.md#k-48)):** sender profil ile split — prod `SmtpMailSender` (`spring.mail.*` → `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`; `MAIL_HOST` boşsa startup fail-fast), dev `LogMailSender` (log'a düşer), test `InMemoryMailSender`. Ortak config `forgesys.mail.*`: `from`, `default-language` (tr default), `templates-dir` (classpath `mail/*.html` override — `infra/templates/`). Şablonlar TR/EN: tenant-verify, email-verify, password-reset.
- **Actuator/metrics ([K-43](DECISIONS.md#k-43)):** dev/test — same-port, `health,info,metrics,prometheus` (scrape auth'suz). Prod — ayrı management portu **8081** (compose'da `expose`-only, host'a publish edilmez; management child context'inde security zinciri uygulanmaz → internal ağdan auth'suz scrape) + daraltılmış exposure (`health,info,prometheus`). Custom gauge: `forgesys.tenants.active`.

## İlgili Dokümanlar

- [`AGENTS.md`](../AGENTS.md) — AI asistan kuralları + genel proje prensipleri
- [`README.md`](../README.md) — Kurulum, çalıştırma, API
- [`ROADMAP.md`](ROADMAP.md) — Faz/epik yol haritası (ticket numarasız)
- [`DECISIONS.md`](DECISIONS.md) — Karar kayıtları (K-XX/RISK-XX/DEBT-XX)
- [`backend/AGENTS.md`](../backend/AGENTS.md) — Backend modülü kuralları + gotcha'lar
- [`persistence/AGENTS.md`](../persistence/AGENTS.md) — Persistence modülü, entity hiyerarşisi, Flyway
- [`common/AGENTS.md`](../common/AGENTS.md) — Common çekirdek (TenantContext)
- [`frontend/AGENTS.md`](../frontend/AGENTS.md) — Frontend stack & yapı
