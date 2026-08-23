# Yol Haritası (Roadmap)

> Stratejik faz/epik planı. Fazlar sıralı bağlam, epic'ler iş kümeleri. **Önceden numaralandırılmış ticket tablosu YOK** — işler epic bazında, amaca odaklı tanımlı. Geliştirici kendi SF-NN tag'ini (branch/commit) verir, bu dosyaya bağımlı değil.

## Durum Etiketleri

- `TODO` — başlanmadı
- `DOING` — aktif
- `DONE` — tamamlandı
- `[BLOCKED]` — engelli (notu ile)
- `CANCEL` — iptal (gerekçe ile)

## Mevcut Durum (Faz 2 backend + IAM hardening + pm modülü + admin console — DONE)

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

**Faz 2.5 (DONE) — Auth endpoints:**
- [x] `POST /auth/login` (cookie + body access token), `GET /auth/me`, `POST /auth/logout` (cookie expire)
- [x] Refresh token + Redis blacklist (K-34 — DONE; rotasyon + reuse detection + per-session logout, bkz. Epic 2.5/2.6 notu)

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

## Faz 1.5 — Nginx Topology Refactor ([BLOCKED] — K-18, plan netleşti K-33)

> K-18 (2026-07-09) ile Faz 2 sonrasına ertelendi; **K-33 (2026-07-25) ile plan netleştirildi ve uygulama proje %90 tamamlanana kadar ertelendi.** Aşağıdaki epic'ler toplamda sayılır ama aktif değil. `@Transactional` fix (1.5.A) Faz 2'de K-21 ile çözüldü. Topology planı (shared gateway + Let's Encrypt wildcard DNS-01, Cloudflare yok) için [DECISIONS.md K-33](DECISIONS.md#k-33).

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
- [x] `RequestLoggingFilter` + traceId (MDC) + `X-Request-Id` header + log pattern — `RequestMetadataFilter` (K-19, 2026-07-27) traceId/MDC/`X-Request-Id`'yi getirdi; full request logging de geldi (`RequestLogFilter` + `t_request_logs`, K-27/2026-08-23)
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

### Epic 2.6 — Redis (kısmen DONE — refresh/blacklist; PermissionCache erteli)
- [x] `TokenBlacklistService` (granular access-token `jti` revoke) + Redis/InMemory profilli impl'ler + unit/IT test. **RedisConfig YOK** — store'lar auto-config `StringRedisTemplate` (Redis hash + Lua) kullanır; Jackson2 JSON serializer Jackson 3 ile uyumsuzdu ve gereksizdi (K-34).
- [ ] `PermissionCacheService` (TTL 10dk, role/group mutation'larinda evict) — **erteli** (yetkiler JWT'ye gömülü, cache sadece login/refresh mint'i optimize eder — düşük değer).

> **Durum (2026-07-30):** Refresh token + TokenBlacklistService K-34 ile geldi. Bağlantı altyapısı (`spring.data.redis.*`) + dev Redis container zaten hazırdı. PermissionCacheService erteli.

### Epic 2.5 — Auth Endpoints (DONE — login + refresh + rotation/reuse + per-session logout)
- [x] `AuthService.login()` iş mantığı (BCrypt doğrulama, unknown/bad-password aynı `auth_bad_credentials`)
- [x] LoginRequest/LoginResponse DTO
- [x] `POST /api/v1/auth/login`: Set-Cookie (httpOnly `sf_access_token` + `sf_refresh_token`) + body. Refresh opaque, Redis'de hash-at-rest.
- [x] Login integration test (valid -> cookie+200, wrong -> 401, unknown -> 401)
- [x] `GET /api/v1/auth/me` (claim'den principal, DB'siz)
- [x] `POST /auth/logout`: **per-session (K-34)** — refresh consume + mevcut access `jti` blacklist (granular tek-token revoke). `tokenInvalidBefore` set ETMEZ (o password change/reset/reuse'e özel).
- [x] `POST /api/v1/auth/refresh` (K-34) — permitAll; tenant TenantFilter'dan; authorities DB'den re-resolve (taze yetkiler + locked/disabled re-check).
- [x] Refresh token rotation + reuse detection (ihlal -> tüm refresh revoke + `tokenInvalidBefore`) — **K-34**.
- ~~JwtAuthFilter blacklist hook~~ — DONE (K-34): filter artık `jti` blacklist'i kontrol eder.

> **RISK-21 (tokenInvalidBefore) + K-34 (jti blacklist):** İki katmanlı revoke — user-scoped (`tokenInvalidBefore`, password change/reset/reuse) + granular per-session (`jti` blacklist, logout). Çalınan token anında geçersiz (logout/blacklist) veya şifre değişiminde (tokenInvalidBefore). Refresh reuse → tüm session'lar düşürülür.


### Epic 2.7-2.8 — Wrap-up
- [x] `AuditorAware` SecurityContext userId (RISK-3/[RISK-33]) — Çözüldü 2026-07-24; artık SecurityContext userId + `"system"` fallback (`MultiTenancyJpaConfig`)
- [x] Bootstrap data initializer (rol/permission/group seed, idempotent, diff-based) — **K-24** (`SystemAdminBootstrapRunner` + `RbacSeeder` + `PermissionCatalog`)
- [ ] springdoc-openapi dep + Swagger scheme doc (profile gating) — pom'da dependency YOK, erteli

### Epic 2.9 — User & RBAC Management (K-18, K-26) — DONE
> User CRUD + yetki atama/silme + user page + rol-bazlı panel. `@EnableMethodSecurity` + `@PreAuthorize` method-level yetkilendirme (K-26 — uygulandı). Permission namespace `{module}:{resource}:{action}`.

- [x] `UserController` (`/api/v1/users`: sayfalı list / GET{id} / POST / PUT{id} / DELETE{id}) + DTO
- [x] `UserService` (`@Transactional`, soft-delete, account+profile cascade)
- [x] `UserProfileController` (`PUT /users/me/profile`, `PUT /users/me/password`, `GET /users/me`) — self-service, `@PreAuthorize`'süz authenticated
- [ ] Email doğrulama akışı (tenant **içi** mevcut user — speculative kolonlar K-38 ile kaldırıldı; akış kendi migration'ını getirir)
- [ ] Password reset akışı (speculative kolonlar K-38 ile kaldırıldı; akış kendi migration'ını getirir)
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

**K-19 çekirdek (3 katmanlı log):** — **DONE (2026-07-27 + 2026-08-23):** `t_audit_logs` + `t_login_history` (append-only V6 trigger + yetki değişim delta kaydı dahil, Faz IAM 2 ile), `GET /audit-logs` + `GET /login-history` (`iam:audit:read`, sayfalı + filtre + `q` araması); 3. katmanın tablosu `t_request_logs` + `GET /request-logs` + admin UI 2026-08-23'te tamamlandı (K-27 kısmi uygulamasıyla).

**K-27 genişletme (audit/log/security hardening):** — **kısmen DONE (son parça 2026-08-23):** audit append-only trigger + yetki değişim old/new delta kaydı (Faz IAM 2); `@AuditLog` AOP, high-risk request body loglama (mask-first), request-logs tablosu/endpoint'i (`t_request_logs` + `GET /request-logs`) + admin UI (`RequestLogsPage`) 2026-08-23'te geldi. Bilinçli ertelenen: `t_pending_actions` approval workflow, anomaly detection.

**K-28 session management:** — **DONE (2026-07-30):** Redis active sessions + `/users/me/sessions` (self) + `/users/{id}/sessions` (admin) + `DELETE .../sessions/{sessionId}` (remote revoke — access token `tokenInvalidBefore` ile anında düşer) + max concurrent session limiti (`forgesys.security.max-sessions`). `t_sessions_log` ertelendi (`t_login_history`/`t_audit_logs` ile örtüşme).

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

### Epic 2.11 — IAM Hardening (DONE 2026-07-30 – 2026-08-17)

> Detaylı faz kayıtları: kök [`AGENTS.md`](../AGENTS.md) "Faz IAM" bölümü.

- [x] Yetki-sonrası session revoke — rol/izin/grup değişiminde etkilenen kullanıcıların access + refresh token'ları anında düşer (`SessionRevocationService`; privilege-retention penceresi kapandı)
- [x] Max concurrent session limiti (`forgesys.security.max-sessions`, en eski session düşürülür)
- [x] Audit append-only (V6 trigger) + yetki değişim delta kaydı (old/new JSON)
- [x] App-level rate limiting — public auth endpoint'lerinde Redis Lua token-bucket (`RateLimitFilter`, JWT decode'dan önce; Nginx edge limit'i K-33 gateway epic'ine ertelendi)
- [x] Rol kalıtımı (`t_role_parents`, V7; cycle guard + recursive authority çözümlemesi) + ABAC ownership şablonu (`Ownable` + `OwnershipGuard` — şablon K-38 ile kaldırıldı, ilk ABAC modülüyle geri gelir)
- [x] `all_permissions` bayrağı (V8) — Admin implicit süper-kullanıcı; `PUT /roles/{id}/permissions` `{all:true}` kısayolu
- [x] Last-admin invariant ([RISK-35](DECISIONS.md#risk-35)) — self-delete koşulsuz yasak, son aktif admin kaybedilemez (11 write path)
- [x] Permission CRUD + user/group effective-permissions endpoint'leri
- [x] User directory read model (`UserDirectoryView` `@Subselect`) + `iam:group-member:read` scoped görünürlük + `GET /users/{id}/activity` + admin unlock (`DELETE /users/{id}/lock`) + audit/login-history araması
- [x] Güvenlik düzeltmeleri: RbacSeeder startup privilege escalation kapandı ([RISK-36](DECISIONS.md#risk-36)); aktif lockout refresh'i de blokluyor; refresh revoke rotasyon zincirini takip ediyor

---

## Faz 3 — Modüler Platform (Module System + Built-in Modüller)

> Hibrit modüler platform: önce Module System altyapısı (3.0), sonra built-in modüller (3.1-3.4). Custom App Builder backend altyapısı da 3.0 ile gelir; UI'sı Faz 4.2 (DONE).

### Epic 3.0.A — Module System & Plan/Subscription (K-16) — DONE (2026-08-22)
> Uygulama kararları: [DECISIONS.md K-16](DECISIONS.md#k-16). Registry kodda (`ModuleDefinition`/`PlanDefinition` enum — `t_module_catalog` tablosu yapılmadı), modül-başı ayrı Flyway history (`db/migration/module/<key>` + `flyway_schema_history_mod_<key>`), `iam:module:read/write` + `GET /api/v1/modules` + `POST /modules/{key}/activate` (3.0.C'in modül parçası öne alındı). Gerçek PG doğrulaması: `ModuleActivationIT` (gated).

- [x] `public` migration `V2__plans_subscriptions_modules.sql`: `t_plans`, `t_subscriptions`, `t_tenant_modules` (tenant-başı tek abonelik + `(company_id, module_key)` partial unique)
- [x] `Plan`/`Subscription`/`TenantModule` entity'leri + repository'ler (public schema)
- [x] `PlanDefinition` (FREE/PRO/ENTERPRISE) + `PlanSyncRunner` (`@Order(0)`, idempotent upsert)
- [x] `ModuleDefinition` registry (key/name/minPlan/flywayLocation/permissions) — `pm` modüle çevrildi (FREE, baseline tabloları, permission'ları modül sahipliğine taşındı)
- [x] `PermissionCatalog` split: `CORE` (iam+platform+`iam:module:*`) vs modül permission'ları; `RbacSeeder` core-only
- [x] `TenantMigrationSupport.migrateModule` — modül-başı history tablosu + baseline-0
- [x] `ModuleActivationService` (plan gate → migrate → permission seed (REQUIRES_NEW) → kayıt (caller tx)) + `activateDefaultModules` + `resyncForCompany`
- [x] `ModuleSyncRunner` — mevcut tenantlara FREE backfill + default modüller + aktif modül re-sync
- [x] Tenant signup → FREE subscription + default modüller (`verifyAndProvision` hook)
- [x] `GET /modules` + `POST /modules/{key}/activate` + ErrorCode'lar (`module_not_found`, `module_already_active`, `subscription_not_found`, `module_plan_required`) + frontend permission mirror
- [x] Testler: unit (activation/sync/plan-seed) + controller (H2) + `ModuleActivationIT` (Testcontainers, gerçek PG: provisioning hook + permission seed + history izolasyonu)

### Epic 3.0.B — Custom App Builder Backend (K-15, Notion-style) — DONE (2026-08-22)
> Uygulama kararları: [`DECISIONS.md K-15`](DECISIONS.md#k-15). `apps` modül olarak geldi (`db/migration/module/apps` ağacının ilk kullanımı, FREE + default); JSONB mapping düz String (hypersistence-utils EKLENMEDİ); FORMULA ertelendi; MapStruct yerine manuel `toResponse` (mevcut konvansiyon).

- [x] `apps` modülü: `t_apps`, `t_app_properties (config jsonb)`, `t_app_records`, `t_app_record_values (value jsonb, GIN jsonb_path_ops)`, `t_app_views (config jsonb)` — modül-başı Flyway history (`flyway_schema_history_mod_apps`)
- [x] `AppBuilderService` (app/property/view CRUD + tip/config doğrulama) + `AppRecordService` (record CRUD, PATCH partial-merge, required coverage)
- [x] Property type validators (TEXT/NUMBER/SELECT/DATE/USER/RELATION — FORMULA reddedilir, `AppPropertyValueValidator`)
- [x] `AppRecordSearchExecutor` — native PG JSONB sorgu (`@>`/`#>>`/`::numeric`, GIN-backed; H2'de koşmaz)
- [x] Limit enforcement (maxApps/maxRecordsPerApp kod registry — FREE 3/1k, PRO 25/50k, ENT sınırsız; soft-block 403 `app_limit_reached`, `PlanLimitService`)
- [x] Testler: 34 unit + 29 H2 controller + `AppBuilderIT` (gated gerçek PG: aktivasyon + JSONB search + izolasyon)
- [x] View config güvenliği spike'ı ÇÖZÜLDÜ: structured JSON DSL (`AppQueryValidator` + `AppViewConfigValidator`) — serbest expression dili yok, injection yüzeyi yapısal olarak kapalı

### Epic 3.0.C — Module/App API
- [x] `GET /api/v1/modules` (katalog + aktif) + `POST /modules/{key}/activate` — **3.0.A ile geldi** ([K-16](DECISIONS.md#k-16))
- [x] `GET/POST/PATCH/DELETE /api/v1/apps` (custom app CRUD) + `/apps/{id}/records` (+ `/search`, PATCH) + `/properties` + `/views` — **3.0.B ile geldi** ([K-15](DECISIONS.md#k-15))
- ~~MapStruct mappers (`AppMapper`, `RecordMapper`, `ViewMapper`)~~ — iptal: Epic 2.1 MapStruct hiç uygulanmadı, kod tabanı manuel `toResponse` konvansiyonunda (yeni dependency değmez)

### Epic 3.1 — Built-in "Tasks" Modülü — DONE (pm modülü olarak)
> Görev yönetimi standalone yerine **project-scoped** geldi (2026-08): eski `tenant/V4__module_projects.sql` + `V5__module_tasks.sql` (K-36 squash'ı ile `V1.3__pm_projects_tasks.sql`'e indirildi) — tip-bazlı proje yapısı (`t_projects`), TASKS tipinde `t_tasks` (proje-scoped) + Kanban board UI (Epic 4.1). `pm:*` permission namespace. Standalone Tasks modülü bu sayının yerini aldı; Notes/Warehouse/Logistics (3.2-3.4) planlandığı gibi.

### Epic 3.2 — Built-in "Notes" Modülü — DONE (2026-08-23, [K-44](DECISIONS.md#k-44))
> Standalone + tenant-shared + markdown + default-aktif kararlarıyla geldi (APPS modül deseni: `ModuleDefinition.NOTES`, `db/migration/module/notes`, bağımsız Flyway history). `t_notes` + `t_note_categories` (`ON DELETE SET NULL`); `notes:note:*` + `notes:category:*` permission'ları; `/api/v1/notes` (`?q=` + `?categoryId=` + `?pinned=`) + `/api/v1/note-categories`; UI: NotesPage (DataTable + kategori filtre + pinned toggle) + NoteEditorPage (markdown edit/preview — `react-markdown`, raw HTML kapalı). ABAC görünürlük + WYSIWYG + full-text search bilinçli erteli (K-44 "bilinçli yapılmayanlar").

### Epic 3.3 — Built-in "Warehouse" Modülü
`tenant` migration (sonraki versiyon): `t_products`, `t_warehouses`, `t_stock_items`, `t_stock_movements`. Entity'ler (Product/Warehouse/StockItem/StockMovement) + service + controller. Stok hareketleri (IN/OUT/TRANSFER) + minimum stok uyarısı.

### Epic 3.4 — Built-in "Logistics" Modülü
`tenant` migration (sonraki versiyon): `t_shipments`, `t_vehicles`, `t_drivers`, `t_routes`. Entity'ler (Shipment/Vehicle/Driver/Route) + service + controller. Sevkiyat durum makinesi (CREATED -> IN_TRANSIT -> DELIVERED).

### Epic 3.X — Testcontainer + Rate Limit — DONE
- [x] Testcontainers: iki gerçek tenant şeması + `SET search_path` izolasyonu + RISK-26 mid-tx switch doğrulaması (`CrossTenantIsolationTest`, `-Dforgesys.pg.it=true` gate'i)
- [x] Rate limiting (Redis Lua token-bucket — Epic 2.11 ile app-level geldi; edge `limit_req` K-33 gateway epic'ine ertelendi)

---

## Faz 4 — Frontend (Modüler UI + Custom App Builder UI)

### Epic 4.0 — Frontend Core
- Bağımlılıklar (TanStack Query, Zustand, Tailwind, react-router)
- Tailwind setup + klasör mimarisi
- Auth UI (login/register) + Zustand auth store + Axios interceptor
- Modül-bazlı sidebar (`GET /modules` -> aktif modüller) + aktivasyon ekranı
- `App.tsx` parçala + React Router route yapısı

### Epic 4.0.B — Admin/User/Log Management UI (K-20) — DONE (2026-08)
> Faz 4 core stack kuruldu ve tamamı ship edildi: login/register/verify sayfaları + Zustand auth store + axios interceptor (cookie, transparent refresh); data-driven lazy routing + permission-gated navigation (`RequirePermission`); users/roles/groups/permissions/sessions (self + admin)/audit-logs/login-history/projects sayfaları; user detail (aktivite geçmişi, unlock, effective-permissions, diff-based sequential save); profile page; request-log sayfası 2026-08-23'te eklendi (K-27 ile) — epic tamamlandı.

### Epic 4.1 — Built-in Modül UI'ları — kısmen DONE
Tasks UI (liste + Kanban board — DONE, pm modülüyle birlikte). Notes UI (rich-text + kategori), Warehouse UI (ürün/stok tablosu + hareketler), Logistics UI (sevkiyat listesi + durum güncelleme) — TODO.

### Epic 4.2 — Custom App Builder UI (Notion-style — en iddialı) — DONE (2026-08-23)
> Uygulama kararları: [`DECISIONS.md K-42`](DECISIONS.md#k-42). 3 session'da ship edildi (1/3 temel `9a8004d`, 2/3 view renderer'lar `73e68c2`, 3/3 edit modalı + plan göstergesi + kapanış). Bilinçli yapılmayanlar: **drag-drop YOK** (kart taşıma TaskBoard emsalindeki select/move mover ile — PATCH), **expression editor YOK** (satır bazlı structured DSL — backend `AppQueryValidator`'ın 9 op'u, injection yüzeyi yok), CALENDAR'da kayıt aksiyonu yok (chip'ler yer kısıtlı; TABLE/BOARD/LIST/GALLERY'den düzenlenir). Filter/sort client-side uygulanır (`GET /records` tek sayfa, cap 1000 — `records/search` PG-only olduğu için; üstü "ilk 1000" notuyla).

- [x] App designer (isim/açıklama + emoji ikon shortlist — sihirbaz değil modal; ikon list/detail'de gösterilir)
- [x] Property editor (tip seçimi + config — SELECT options / RELATION target; FORMULA listelenir ama devre dışı)
- [x] Record editör: satır içi düzenleme (TABLE) + tam form modalı (create + edit — prefill, PATCH partial-merge diff, required-clear satır-içi bloklu)
- [x] TABLE view renderer (kolon=property, satır=record; server pagination / client-mode switch)
- [x] BOARD view renderer (Kanban — groupBy SELECT options kolonlar + "değersiz" kovası; taşımayı yükleme `apps:record:write` ile geçilen taşıyıcıyı seç)
- [x] CALENDAR view renderer (dateProperty'e göre ay/hafta ızgarası, Mon-first, bugün/önceki/sonraki)
- [x] GALLERY + LIST view renderer (kart grid / kompakt satır + client pagination)
- [x] Filter/sort config UI (satır bazlı DSL; op listesi property tipinden türetilir; "Uygula" anlık geçici filtre, "Görünüme kaydet" PUT) — drag-drop ve expression editor bilinçli yapılmadı (yukarıda)
- [x] Relation picker (hedef app kayıt araması, ilk TEXT property başlık) + User picker (directory typeahead) + hücrelerde id→label çözümleme (email / hedef başlık)
- [x] Plan limit göstergesi: `GET /api/v1/apps/plan-limits` (K-42 — sayılar backend `PlanDefinition` registry'sinden; frontend'e sabit kopya YOK) + AppsPage "x / y uygulama" progress'i + detayda "x / y kayıt" sayacı

---

## Faz 5 — Hardening & Operasyon

- [ ] **TLS termination:** `nginx.conf` Let's Encrypt (certbot) veya external certs. HTTP -> HTTPS redirect. HSTS header.
- [x] **Observability (metrics expose — K-43, 2026-08-23):** `micrometer-registry-prometheus` + `/actuator/prometheus` text format expose. Exposure: dev/test `health,info,metrics,prometheus` (same-port, scrape auth'suz — permitAll), prod `health,info,prometheus` + ayrı management portu 8081 (compose'da expose-only internal ağ — asla publish edilmez; scraper bu ağa bağlanır ya da K-33 gateway üzerinden). Business gauge: `forgesys.tenants.active` (tek platform-level gauge; tenant-içi seriler bilinçli yok — scrape thread'inde tenant şeması çözümlenemez). Prometheus/grafana stack'i K-33 gateway ile ayrı iş. OpenTelemetry tracing erteli kalır.
- [x] **CI/CD (GitHub Actions):** `.github/workflows/ci.yml` — PR'da 3 paralel job: backend (`mvn clean install -pl backend -am`, H2) + frontend (`npm run lint` + `npm test` + `npm run build`) + **integration** (gated IT'ler açık: `CrossTenantIsolationTest`/`ModuleActivationIT`/`AppBuilderIT` gerçek PG + `RedisRefreshTokenIT` gerçek Redis, Testcontainers). CD: develop/main push → tüm job'lar yeşilse root `Dockerfile` build + **GHCR** publish (`GITHUB_TOKEN` — ek secret yok; main → `:latest`, develop → `:edge`, her ikisi `:sha-<short>`; GHA build cache).
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
