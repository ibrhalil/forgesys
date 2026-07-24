# Yol Haritası (Roadmap)

> Stratejik faz/epik planı. Fazlar sıralı bağlam, epic'ler iş kümeleri. **Önceden numaralandırılmış ticket tablosu YOK** — işler epic bazında, amaca odaklı tanımlı. Geliştirici kendi SF-NN tag'ini (branch/commit) verir, bu dosyaya bağımlı değil.

## Durum Etiketleri

- `TODO` — başlanmadı
- `DOING` — aktif
- `DONE` — tamamlandı
- `[BLOCKED]` — engelli (notu ile)
- `CANCEL` — iptal (gerekçe ile)

## Mevcut Durum (Faz 2.9 backend + K-21 — DONE)

Backend altyapısı + auth/RBAC/platform management + iki-fazlı tenant signup tamamlandı:

**Faz 1 (DONE) — altyapı:**
- [x] Multi-module Maven yapısı (`common` <- `persistence` <- `backend` + `frontend`)
- [x] Schema-per-tenant multi-tenancy: subdomain çözümleme + Hibernate `SCHEMA` stratejisi
- [x] Flyway per-schema migration (public auto-config + tenant programmatik)
- [x] Tenant signup endpoint: `POST /api/v1/auth/company/register` (tek-fazlı senkron — `provisionTenant` ACTIVE + şema + Flyway + admin user). İki fazlı akış K-21 ile planlandı (Epic 2.0.C).
- [x] Entity hiyerarşisi: UUID, soft delete, optimistic locking, Spring Data auditing
- [x] BCrypt password encoding, Bean Validation, merkezi hata yönetimi (`ErrorResponse`)
- [x] Docker: PostgreSQL + Redis + app (non-root), layered jars, actuator health

**Faz 2.0.C (DONE) — K-21 iki fazlı tenant signup:**
- [x] `createPendingCompany` (PROVISIONING + token + verify maili) + `verifyAndProvision` (senkron schema + Flyway + admin user → ACTIVE)
- [x] `SubdomainSuggestionService` (slug önerisi, Türkçe karakter normalize)
- [x] K-32: `email_domain` kaldırıldı, `t_organization_domains` (1:N, opsiyonel, `verified=false` default)
- [x] `provisionSystemTenant` (K-24 bootstrap auto-verify)

**Faz 2.0 (DONE) — Foundation + critical fixes:**
- [x] DateTimeProvider (UTC, RISK-15), uniform error shape, exception hiyerarşisi
- [x] `TenantMigrationRunner` (RISK-16), partial index UNIQUE (RISK-17), hashCode fix (DEBT-7)

**Faz 2.3/2.4 (DONE) — Security + JWT:**
- [x] Spring Security (STATELESS + CSRF off + JSON 401/403 handlers)
- [x] `PepperingPasswordEncoder` (K-23, BCrypt(12) + HMAC-SHA256 pepper)
- [x] `JwtTokenProvider` (RS256, oauth2-resource-server) + `JwtAuthenticationFilter` (cookie→SecurityContext)
- [x] `CustomUserDetails(Service)` — authorities = direct roles + active group roles → permissions

**Faz 2.5 (kısmen DONE) — Auth endpoints:**
- [x] `POST /auth/login` (cookie + body access token), `GET /auth/me`, `POST /auth/logout` (cookie expire)
- [ ] Refresh token + Redis blacklist (Epic 2.6 ile)

**Faz 2.7-2.8 (kısmen DONE) — Wrap-up:**
- [x] `SystemAdminBootstrapRunner` + `RbacSeeder` + `PermissionCatalog` (K-24, K-25)
- [x] `AuditorAware` SecurityContext userId (RISK-3/[RISK-33] — Çözüldü 2026-07-24)
- [ ] springdoc-openapi

**Faz 2.9 (DONE) — User & RBAC + Platform management:**
- [x] User/Role/Permission/Group CRUD + assign/revoke + DTO (`@Transactional`, soft-delete)
- [x] `@EnableMethodSecurity` + `@PreAuthorize` enforcement (K-26)
- [x] Self-service (`/users/me/**`): profile update + password change + me
- [x] Platform admin namespace (K-25): `/platform/companies` list/get/status
- [x] Admin password reset (`PATCH /users/{id}/password`)
- [ ] Tenant içi user email doğrulama + password reset akış (entity field'ları hazır, flow yok)

---

## Faz 1.5 — Nginx Topology Refactor ([BLOCKED] — K-18)

> K-18 (2026-07-09) ile Faz 2 sonrasına ertelendi. Aşağıdaki epic'ler toplamda sayılır ama aktif değil. `@Transactional` fix (1.5.A) Faz 2'de K-21 ile çözülür.

### Epic 1.5.A — @Transactional Fix
`provisionTenant()` ve `createAdminUser()` transactional. Helper metotlar `readOnly`. Rollback testi (partial writes). Detay: [DEBT-10](DECISIONS.md#debt-10).

### Epic 1.5.B — Nginx Gateway Config
`nginx/nginx.conf` (rate limit, headers, gzip), `conf.d/default.conf` (routing), `nginx/Dockerfile`, config validation testi.

### Epic 1.5.C — Frontend Docker Ayrımı
`frontend/pom.xml` kaderi kararı (spike), `frontend/Dockerfile` multi-stage, `backend/pom.xml`'den frontend plugin kaldır, kök `Dockerfile` backend-only, standalone docker build testi.

### Epic 1.5.D — dev-full Compose
`docker-compose.dev-full.yml` (5 servis + JPDA), network + depends_on + healthcheck, integration test (routing + rate limit), `vite.config.ts` proxy koru.

### Epic 1.5.E — prod Compose
`docker-compose-prod.yml` 3-container, TLS cert path placeholder, `.env.example` (nginx + JWT vars).

---

## Faz 2 — Kimlik Doğrulurma & RBAC + Log + Yönetim

> Sıralama önemli — her alt-faz bir sonrakinin ön koşulu. K-18 sonrası Faz 1.5 atlandı, doğrudan Faz 2. Backend-önceli sıralama: tüm Faz 2 backend bitince Faz 4.0.B frontend gelir.

### Epic 2.0 — Foundation Refactors
Cross-cutting iyileştirmeler. Auth işinden önce yapılmalı.
- [x] `DateTimeProvider` bean (UTC) — RISK-15 çözüldü
- [x] `ApiErrorResponse`/`ApiFieldError`/`ApiErrorFactory` (uniform error shape + traceId)
- [x] Exception hiyerarşisi: `BusinessException` -> `AuthException`/`ResourceNotFoundException` + stable error codes
- [x] `sanitizeRejectedValue` (validation error'da password/token maskeleme)
- [ ] `RequestLoggingFilter` + traceId (MDC) + `X-Request-Id` header + log pattern — `ApiErrorFactory` MDC traceId'yi zaten okuyor (filter yoksa UUID üretir); full request logging ertelendi
- [ ] `PasswordEncodingListener` (JPA `@PrePersist`/`@PreUpdate`, şifre otomatik encode) — ertelendi

### Epic 2.0.B — Critical Fixes
Kod analizi sonucu keşfedilen P0 düzeltmeler. User CRUD / log'dan ÖNCE çözülmeli.
- [x] `TenantMigrationRunner` (`ApplicationRunner`, `@Profile("!test")`) — startup'ta tüm `t_companies` şemalarını `TenantMigrationSupport` üzerinden Flyway migrate (RISK-16 — ÇÖZÜLDÜ). V2 tenant migration'ından ÖNCE gelmeli.
- [x] UNIQUE -> partial index (`WHERE is_deleted=false`): public `t_companies` (name/subdomain/email_domain/schema_name) + tenant `t_users`/`t_roles`/`t_permissions`/`t_groups`. `public/V2` + `tenant/V2` (RISK-17 — ÇÖZÜLDÜ). User CRUD'dan ÖNCE.
- [x] `hashCode()` düzelt — hem `BaseEntity` hem `GeneratedIdAuditEntity` (ID-bazlı, DEBT-7 — ÇÖZÜLDÜ). RBAC'dan ÖNCE.
- [ ] `TaskDecorator` — TenantContext + SecurityContext propagation (`@Async`, RISK-10). **Ertelendi:** ileriye dönük altyapı; şu an `@Async` tüketici yok. İlk async iş (audit/email) ortaya çıkınca, Faz 2.3 auth sonrasına bırakıldı.

### Epic 2.0.C — Hibrit Tenant Signup Verification (K-21) — DONE
> K-21 kararı — UYGULANDI. Detay: [DECISIONS.md K-21](DECISIONS.md#k-21). K-32 (organizasyon/domain refactor) ile birlikte uygulandı.

İki fazlı signup akışı:
- [x] `TenantVerificationToken` entity (`public` şema, `GeneratedIdAuditEntity`) + `public/V3__organization_domains_and_verification_tokens.sql` migration + `TenantVerificationTokenRepository`
- [x] `VerificationSender` interface + `InMemoryVerificationSender` (`test`) + `LogVerificationSender` (`!test`) — mail bağımlılığı YOK (prod mail Faz 5)
- [x] `TenantProvisioningService` bölündü: `createPendingCompany()` (PROVISIONING, `@Transactional`) + `verifyAndProvision(token)` (ACTIVE, `@Transactional`, senkron) + `provisionSystemTenant()` (K-24 bootstrap auto-verify)
- [x] DTO (`CompanyRegisterResponse`, `CompanyVerifyRequest`, `CompanyVerifyResponse`, `SubdomainSuggestionRequest/Response`) + `AuthController`: `register` 202 PROVISIONING + `POST /api/v1/auth/company/verify` + `POST /api/v1/auth/company/suggest-subdomain`
- [x] `SubdomainSuggestionService` (slug üretimi + Türkçe karakter normalize + uniqueness)
- [x] Service testi (`TenantProvisioningServiceTest` — register→verify + token expire/used/invalid + bootstrap auto-verify; `SubdomainSuggestionServiceTest` — slugify + suggest)
- [x] K-32: `email_domain` kolonu/field/index DROP + `t_organization_domains` (1:N, `verified` boolean — custom domain doğrulama Faz 5)
- [x] `ErrorCode` extension: `COMPANY_SUBDOMAIN_TAKEN`, `TENANT_TOKEN_INVALID/EXPIRED/ALREADY_USED`
- (Ertelendi) Scheduled cleanup job — expired token + bağlı PROVISIONING Company'leri sil
- (Ertelendi, Faz 5) `MailVerificationSender` (`prod`) + `spring-boot-starter-mail` pom'a + SMTP config

### Epic 2.1 — MapStruct + DTO
`persistence/pom.xml`: mapstruct (sıralama KRİTİK), `MappingConfig` (@MapperConfig), DTO record'ları, `AuthMapper` interface, `AuthController`: `Map.of` -> DTO+mapper, MapStruct build testi.

### Epic 2.3 — Spring Security Core (tek PR) — DONE
> `spring-boot-starter-security` tek başına default form login getirir, app'i kırar. Bu yüzden security setup tek PR'da commit edilmeli.

- [x] BCrypt migration stratejisi spike (RISK-13) — lazy: mevcut strength-10 hash'ler BCrypt self-describing olduğu için validate olur, yenileri 12'de
- [x] `pom.xml`: spring-boot-starter-security
- [x] `SecurityConfig`: filterChain + STATELESS + CSRF
- [x] `BCryptPasswordEncoder(12)`
- [x] `CorsConfig`: CorsConfigurationSource
- [x] JSON 401/403 handlers (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) — uniform shape + traceId
- [x] Security smoke test (401 + permitAll)
- [x] Ekstra: `TenantFilter` security zincirinden ÖNCE (`FilterRegistrationBean`, order -101) çalışacak şekilde kayıtlı

### Epic 2.4 — JWT Infrastructure (oauth2-resource-server + RSA) — DONE
> jjwt yerine `spring-boot-starter-oauth2-resource-server` (Nimbus). RSA asimetrik imzalama (RS256). RISK-14: auto-config filter AKTİF EDİLMEZ, custom `JwtAuthenticationFilter` gerekli.

- [x] `pom.xml`: oauth2-resource-server (jjwt DEĞİL)
- [x] `RsaKeyProperties` record + `certs/*.pem` `.gitignore`'da + openssl keygen doc (RsaKeyProperties javadoc'unda) + **ephemeral key fallback** (dev/test, uyarı loglu)
- [x] `JwtConfig` (JwtEncoder/JwtDecoder bean, RS256, shared KeyPair) + `RsaKeys` (PEM parse + ephemeral) + `JwtTokenProvider` (Spring JwtClaimsSet)
- [x] `JwtTokenProvider` unit test (üret -> decode -> claims + tampered/expired reject)
- [x] `CustomUserDetails` + `CustomUserDetailsService` (tenant-aware, Group -> Role -> Permission) + unit test
- [x] `tokenInvalidBefore` field `UserAccount`'a — **zaten V1 migration + entity'de mevcut** (yeni migration gerekmedi)
- [x] `JwtAuthenticationFilter` (cookie -> decode -> SecurityContext). **NOT:** Redis blacklist + DB `tokenInvalidBefore` kontrolü ERTENDİ (ilk-çalışan-login dilimi için imza+expiry yeterli; revoke logout/refresh ile 2.5/2.6'da)
- [x] SecurityConfig'e filter hook (`.oauth2ResourceServer()` ÇAĞIRILMAZ — RISK-14)
- [x] Filter integration test (cookie -> /me auth, yok -> 401) — `AuthControllerLoginTest` kapsar

### Epic 2.6 — Redis
`RedisConfig`: RedisTemplate + serializer + connection test. `TokenBlacklistService` + unit test. `PermissionCacheService` (TTL 10dk) + unit test.

> **Durum:** Bağlantı altyapısı (`spring.data.redis.*` config) hazır, `application-dev.yaml`'da `repositories.enabled=false`. Gerçek `TokenBlacklistService` / `PermissionCacheService` bean'leri henüz YOK — Epic 2.5 refresh/logout ve token revoke ile birlikte gelir.

### Epic 2.5 — Auth Endpoints (kısmen DONE — ilk çalışan login)
- [x] `AuthService.login()` iş mantığı (BCrypt doğrulama, unknown/bad-password aynı `auth_bad_credentials`)
- [x] LoginRequest/LoginResponse DTO
- [x] `POST /api/v1/auth/login`: Set-Cookie (httpOnly `sf_access_token`) + body'de accessToken. **NOT:** RefreshToken DB ERTENDİ
- [x] Login integration test (valid -> cookie+200, wrong -> 401, unknown -> 401)
- [x] `GET /api/v1/auth/me` (claim'den principal, DB'siz) — **not:** `/auth/me` yerine `/users/me` (UserProfileController) tercih edildi; `/auth/me` halen mevcut
- [ ] `POST /refresh` (Redis + RefreshToken gerekli) — ertelendi
- [ ] `POST /logout`: **Redis blacklist (current access token, granular)** + RefreshToken revoke. `tokenInvalidBefore` KULLANMA (multi-device korunsun). — ertelendi
- [ ] Refresh token rotation + reuse detection (ihlal -> tüm token revoke + `tokenInvalidBefore`) — ertelendi
- ~~JwtAuthFilter blacklist hook~~ — CANCEL (`tokenInvalidBefore` ile gereksiz)

### Epic 2.7-2.8 — Wrap-up
- [x] `AuditorAware` SecurityContext userId (RISK-3/[RISK-33]) — Çözüldü 2026-07-24; artık SecurityContext userId + `"system"` fallback (`MultiTenancyJpaConfig`)
- [x] Bootstrap data initializer (rol/permission/group seed, idempotent, diff-based) — **K-24** (`SystemAdminBootstrapRunner` + `RbacSeeder` + `PermissionCatalog`)
- [ ] springdoc-openapi dep + Swagger scheme doc (profile gating) — pom'da dependency YOK, erteli

### Epic 2.9 — User & RBAC Management (K-18, K-26) — DONE
> User CRUD + yetki atama/silme + user page + rol-bazlı panel. `@EnableMethodSecurity` + `@PreAuthorize` method-level yetkilendirme (K-26 — uygulandı). Permission namespace `{module}:{resource}:{action}`.

- [x] `UserController` (`/api/v1/users`: sayfalı list / GET{id} / POST / PUT{id} / DELETE{id}) + DTO
- [x] `UserService` (`@Transactional`, soft-delete, account+profile cascade)
- [x] `UserProfileController` (`PUT /users/me/profile`, `PUT /users/me/password`, `GET /users/me`) — self-service, `@PreAuthorize`'süz authenticated
- [ ] Email doğrulama akışı (tenant **içi** mevcut user — entity field'ları hazır: `emailVerificationToken`/`ExpiresAt`)
- [ ] Password reset akışı (entity field'ları hazır: `passwordResetToken`/`ExpiresAt`)
- [x] `GET /users/me` (mevcut kullanıcı + permission/rol listesi) — UserProfileController
- [x] `RoleController` (`/api/v1/roles`: CRUD + `/roles/{id}/permissions`)
- [x] `PermissionController` (`/api/v1/permissions`: list — CRUD yok, katalog seed-bazlı)
- [x] `GroupController` (`/api/v1/groups`: CRUD + `/groups/{id}/roles` + `/groups/{id}/members`)
- [x] User-role/group atama (`PUT /users/{id}/roles`, `PUT /users/{id}/groups`)
- [x] `@PreAuthorize` tüm admin endpoint'lerine (K-26 — uygulandı)
- [x] User & RBAC integration test (CRUD + rol atama + tenant izolasyonu + permission red)
- [x] **Ekstra:** `PlatformCompanyController` (`/api/v1/platform/companies` — list/get/status) — K-25 platform admin namespace
- [x] **Ekstra:** admin şifre sıfırlama (`PATCH /users/{id}/password`)

### Epic 2.10 — Audit & Logging (K-19 + K-27/K-28/K-29/K-30 genişletmesi)
> K-19 temel 3 katmanlı log (audit + login history + request/trace). K-27/K-28/K-29/K-30 genişletmeleriyle: başarısız login loglama, high-risk body loglama, anomaly detection, approval workflow, session management, notification subsystem, activity feed.

**K-19 çekirdek (3 katmanlı log):**
- `t_audit_logs` (actor/action/entity/old-new JSONB/request_body JSONB/ip/trace_id) + `t_login_history` (user/success/ip/user_agent/reason enum) — `tenant/V3__audit_login_history.sql`
- `AuditLog` + `LoginHistory` entity + repository (tenant şeması)
- `AuditService` + AOP `@AuditLog` annotation (admin aksiyonları otomatik yakala)
- Login history yazımı — login/refresh/register/logout **+ başarısız denemeler** (K-27)
- Görüntüleme endpoint'leri: `GET /audit-logs`, `GET /login-history`, `GET /request-logs` (admin `@PreAuthorize`, sayfalı + filtre)
- Request log arama — traceId ile lookup

**K-27 genişletme (audit/log/security hardening):**
- High-risk endpoint (create/delete/admin `iam:*`/`platform:*`) request body loglama (maskeli: şifre/token `[REDACTED]`) — config-driven high-risk list
- `t_pending_actions` tablosu + approval workflow (`@ApprovalRequired` veya servis çağrısı) — user/role delete default olarak çift onay
- Anomaly detection passif (rate limit + unusual pattern → K-29 alert, block değil)

**K-28 session management (Epic 2.5/2.6 bağımlı):**
- Redis active sessions (`session:{userId}:{sessionId}` → device/ip/user_agent/loginAt/lastSeen, TTL = refresh token)
- `t_sessions_log` tablosu (tenant) — LOGIN/LOGOUT/SESSION_REVOKED/EXPIRED event'leri (kalıcı audit)
- Endpoint: `/api/v1/users/me/sessions` (self) + `/api/v1/users/{id}/sessions` (admin, `iam:user:write`) + `DELETE .../sessions/{sessionId}` (remote revoke)

**K-29 notification subsystem:**
- `t_notifications` (tenant) + `t_notification_preferences` (user bazlı kanal tercihleri)
- `NotificationService.send(userId, type, payload)` — iki kanal: in-app (polling, WebSocket Faz 5+) + mail (`MailNotificationSender` Faz 5 bağımlı)
- Notification type catalog (SUSPICIOUS_LOGIN, NEW_DEVICE_LOGIN, FAILED_LOGIN_SPIKE, PASSWORD_CHANGED, ROLE_ASSIGNED, BULK_DELETE_ALERT, APPROVAL_REQUESTED, ...)
- Template'ler `infra/templates/` (TR/EN i18n)
- `/api/v1/notifications` (list + mark-read)

**K-30 activity feed:**
- Materialized activity view (audit log üstünden sorgu türetme önerilen, ayrı tablo erteli)
- Activity text generation — `{action}_{entity}` template map (i18n): "Ali 'Tasarım Ekibi' grubunu oluşturdu"
- `/api/v1/activities` (sayfalı, filtreli, visibility scope: public/team/private)
- UI (Faz 4) — activity feed ekranı admin panel'e (K-20) eklenir

> **Sıralama:** K-19 çekirdek önce → K-27 genişletme → K-28 (Epic 2.5/2.6 sonrası) → K-29 notification (audit + anomaly'den beslenir) → K-30 activity (audit log'un user-friendly görselleştirmesi, UI Faz 4).

---

## Faz 3 — Modüler Platform (Module System + Built-in Modüller)

> Hibrit modüler platform: önce Module System altyapısı (3.0), sonra built-in modüller (3.1-3.4). Custom App Builder backend altyapısı da 3.0 ile gelir; UI'sı Faz 4.2.

### Epic 3.0.A — Module System & Plan/Subscription (K-16)
- `public/V2__plan_subscription_modules.sql`: `t_plans`, `t_subscriptions`, `t_tenant_modules`, `t_module_catalog`
- `Plan`, `Subscription`, `TenantModuleActivation`, `ModuleCatalog` entity'leri
- `Module` registry (enum/konfig — key, name, min_plan, flyway_path)
- `ModuleActivationService` (plan kontrol -> Flyway tenant migration -> permission seed -> kayıt)
- `PermissionSeeder` — modül aktivasyonunda `{module}:{resource}:{action}` namespace insert
- Tenant signup -> `t_subscriptions` (default FREE) + varsayılan modüller (Tasks+Notes)
- Module activation integration test (plan reject, Flyway, permission seed)

### Epic 3.0.B — Custom App Builder Backend (K-15, Notion-style)
- `tenant/V2__app_builder.sql`: `t_apps`, `t_app_properties`, `t_app_records`, `t_app_record_values(value JSONB)`, `t_app_views`
- `persistence/pom.xml`: hypersistence-utils + `App*` entity'leri (JSONB mapping)
- `AppBuilderService`: app/property/record/view CRUD + property type validation
- Property type validators (TEXT/NUMBER/SELECT/DATE/USER/RELATION/FORMULA)
- `AppRecordValueRepository` — JSONB GIN index ile sorgu (filter/sort)
- Limit enforcement (max_custom_apps, max_records_per_app — soft-block)
- Custom app builder CRUD testi
- View config güvenliği spike (filter/formula expression injection — sandbox/AST validation)

### Epic 3.0.C — Module/App API
- `GET /api/v1/modules` (katalog + aktif) + `POST /modules/{key}/activate`
- `GET/POST/PATCH/DELETE /api/v1/apps` (custom app CRUD)
- `GET/POST/PATCH/DELETE /api/v1/apps/{id}/records` + `/properties` + `/views`
- MapStruct mappers (`AppMapper`, `RecordMapper`, `ViewMapper`)

### Epic 3.1 — Built-in "Tasks" Modülü
`tenant/V3__module_tasks.sql` (`t_tasks`, `t_task_comments`). Entity + repository. `TaskService` + `TaskController` (`/api/v1/tasks`) + `@PreAuthorize('tasks:task:*')`. Kanban board view API (group by status). CRUD + permission isolation testi.

### Epic 3.2 — Built-in "Notes" Modülü
`tenant/V4__module_notes.sql` (`t_notes`, `t_note_categories`). Entity + service + controller (`/api/v1/notes`). Arama + kategori filtreleme.

### Epic 3.3 — Built-in "Warehouse" Modülü
`tenant/V5__module_warehouse.sql` (`t_products`, `t_warehouses`, `t_stock_items`, `t_stock_movements`). Entity'ler (Product/Warehouse/StockItem/StockMovement) + service + controller. Stok hareketleri (IN/OUT/TRANSFER) + minimum stok uyarısı.

### Epic 3.4 — Built-in "Logistics" Modülü
`tenant/V6__module_logistics.sql` (`t_shipments`, `t_vehicles`, `t_drivers`, `t_routes`). Entity'ler (Shipment/Vehicle/Driver/Route) + service + controller. Sevkiyat durum makinesi (CREATED -> IN_TRANSIT -> DELIVERED).

### Epic 3.X — Testcontainer + Rate Limit
- Testcontainers: tenant signup + modül aktivasyon + CRUD + **veri izolasyonu** e2e (P0 — kritik yol sonu)
- Rate limiting (Redis, IP + tenant bazlı)

---

## Faz 4 — Frontend (Modüler UI + Custom App Builder UI)

### Epic 4.0 — Frontend Core
- Bağımlılıklar (TanStack Query, Zustand, Tailwind, react-router)
- Tailwind setup + klasör mimarisi
- Auth UI (login/register) + Zustand auth store + Axios interceptor
- Modül-bazlı sidebar (`GET /modules` -> aktif modüller) + aktivasyon ekranı
- `App.tsx` parçala + React Router route yapısı

### Epic 4.0.B — Admin/User/Log Management UI (K-20)
> K-20: backend Faz 2 bitince gelir. Faz 4 core stack burada kurulur. Tenant-scoped. Built-in modül UI'ları Epic 4.1'de kalır.

- Login/Register sayfaları + Zustand auth store + axios interceptor (`withCredentials` cookie)
- React Router + auth guard (rol/permission bazlı route koruma)
- Admin panel — **User management** UI (CRUD + rol/grup atama)
- Admin panel — **Role/Permission/Group yönetimi** UI
- **User page** — profil düzenleme + email doğrulama durumu + kendi login geçmişi
- **Audit log sayfası** (filtreli tablo: actor/action/entity/tarih)
- **Login history sayfası** (user/başarı/IP/tarih filtre)
- **Request log sayfası** (traceId arama + seviye filtre)
- Rol-bazlı sidebar (permission'a göre menü göster/gizle)

### Epic 4.1 — Built-in Modül UI'ları
Tasks UI (liste + Kanban board), Notes UI (rich-text + kategori), Warehouse UI (ürün/stok tablosu + hareketler), Logistics UI (sevkiyat listesi + durum güncelleme).

### Epic 4.2 — Custom App Builder UI (Notion-style — en iddialı)
- App designer sihirbazı (isim/ikon/açıklama)
- Property editor (tip seçimi + config — select options/relation/formula)
- Record editör (property type'a göre input widget'ları)
- TABLE view renderer (kolon=property, satır=record)
- BOARD view renderer (Kanban — group_by)
- CALENDAR view renderer (date property'sine göre)
- GALLERY + LIST view renderer
- Filter/sort/group_by config UI (drag-drop, expression editor)
- Relation picker (başka app lookup)
- Plan limit göstergesi (kalan quota)

---

## Faz 5 — Hardening & Operasyon

- [ ] **TLS termination:** `nginx.conf` Let's Encrypt (certbot) veya external certs. HTTP -> HTTPS redirect. HSTS header.
- [ ] **Observability:** actuator + Micrometer -> Prometheus metrics. `management.endpoints.web.exposure.include=health,info,metrics,prometheus`. Internal management portu. OpenTelemetry tracing ertelendi.
- [ ] **CI/CD (GitHub Actions):** `.github/workflows/ci.yml` — PR'da `mvn test` + `npm run lint` + `npm run build`. main push -> Docker build + push registry. Secrets: GitHub Actions secret store.
- [ ] **Ertelenen kararlar değerlendir:** OAuth2 sosyal giriş, WebSocket/SSE, S3/MinIO, OpenTelemetry, microservice geçişi.

---

## Faz 6 — Billing & Abonelik Yönetimi (K-16'nın tamamlanması)

> K-16 plan yapısının finansal tarafı. Faz 3.0'da plan tanımlı; Faz 6 gerçek ödeme + plan yönetim akışını getirir.

- Ödeme sağlayıcı seçimi spike (Stripe vs iyzico — Türkiye pazarı)
- Ödeme sağlayıcı entegrasyonu + webhook dinleme
- Plan upgrade/downgrade akışı (soft-block modül/limit yönetimi)
- Invoice/fatura yönetimi + PDF
- Trial period (14 gün PRO trial yeni tenant'lara)
- Platform admin dashboard (MRR, churn, tenant istatistikleri)

---

## Kritik Yol

Sıralı bağımlılıklar (her faz bir sonrakinin ön koşulu):

**Faz 2:** DateTimeProvider fix (RISK-15, DONE) -> multi-tenancy/UNIQUE/hashCode fixes (RISK-16/17, DEBT-7) -> error altyapısı -> security setup -> JWT infra -> **ilk çalışan login** -> RBAC yönetimi -> audit/log -> UI (Faz 4.0.B)

**Faz 3 başlangıcı:** Module system -> signup+plan -> App Builder backend -> Tasks çalışan -> **izolasyon testi** (Testcontainers, kritik yol sonu)

**Paralel başlanabilir:** Foundation refactors, logging, password encoding listener, mapstruct, redis, openapi.

**En uzun işler (paralel planlama için):** ModuleActivationService, AppBuilderService, App Builder UI bileşenleri, plan upgrade akışı.

---

## İlgili Dokümanlar

- [Karar kayıtları](DECISIONS.md) — K-XX/RISK-XX/DEBT-XX
- [Mimari](ARCHITECTURE.md) — bileşen diyagramı, request lifecycle, schema-per-tenant, entity hiyerarşisi, config profilleri
- [README](../README.md) — kurulum, çalıştırma, API
- [AGENTS.md](../AGENTS.md) (kök + modül bazlı) — AI asistan kuralları
