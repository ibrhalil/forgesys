# Backlog (Atomik Ticket Sistemi)

> Faz bazlı işler **atomik ticketlara** bölünmüştür. Her ticket tek oturumda biten, bağımsız test edilen, tek PR olan iş birimidir. Faz başlıkları (aşağıdaki bölümler) stratejik bağlam; ticketlar operasyonel execution track'idir.

## Ticket Modeli

| Alan | Değerler | Not |
|---|---|---|
| **ID** | `SF-001`... | Sequential, değişmez |
| **P** | `P0` blocker · `P1` high · `P2` medium · `P3` low | |
| **Efor** | `S` <30dk · `M` 30-90dk · `L` 90dk+ (bölünebilir) · `?` önce spike | Tahmin, değişebilir |
| **Type** | `feat` · `fix` · `refactor` · `test` · `chore` · `docs` · `spike` | |
| **Deps** | `SF-YYY` listesi | Bitmeden başlanamaz |
| **Durum** | ⬜todo · 🔄doing · 👀review · ✅done · ⏸blocked · ❌cancel | Emoji + manuel güncelleme |

**Branch convention:** `feat/SF-001-transactional-provision` — ticket ID branch'te, merge sonrası sil.

**Spike tickets** (`?` efor): önce araştırma, çıktısı gerçek efor + alt-ticketlar.

**Ara işler:** Yeni keşfedilen işler sonraki boş ID'yi alır (örn: SF-099 bug).

**Çok-kişili support:** Atanan kişi opsiyonel, paralel dallar bağımsız yürür.

### Status Akışı

```
⬜todo → 🔄doing → 👀review → ✅done
                ↘ ⏸blocked (engel note ile) → 🔄doing
                ↘ ❌cancel (gerekçe ile)
```

---

## Faz 1.5 — Nginx Topology Refactor (⏸ ERTELENDİ — K-18)

> Mimari karar kilitleme sonucu (2026-07-09): 3-container full separation + Nginx dev'de de aktif. **K-18 ile Faz 2 sonrasına ertelendi** — kullanıcı Faz 3 öncesi tam RBAC platformu istiyor. Aşağıdaki ticketlar toplamda sayılır ama aktif değil; `@Transactional` (1.5.A) Faz 2.2'de uygulanır.

### Epic 1.5.A — @Transactional Fix

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-001 | P0 | S | feat | `provisionTenant()` `@Transactional` | — | ⬜ |
| SF-002 | P0 | S | feat | `createAdminUser()` `@Transactional` | — | ⬜ |
| SF-003 | P1 | S | refactor | Helper metotlar `readOnly` | — | ⬜ |
| SF-004 | P1 | M | test | Rollback test (partial writes) | 001,002 | ⬜ |

### Epic 1.5.B — Nginx Gateway Config

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-010 | P1 | M | feat | `nginx/nginx.conf` (rate limit, headers, gzip) | — | ⬜ |
| SF-011 | P1 | S | feat | `conf.d/default.conf` (routing) | — | ⬜ |
| SF-012 | P1 | S | feat | `nginx/Dockerfile` | 010,011 | ⬜ |
| SF-013 | P2 | S | test | Config validation (`nginx -t`) | 012 | ⬜ |

### Epic 1.5.C — Frontend Docker Ayrımı

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-020 | P1 | ? | spike | `frontend/pom.xml` kaderi kararı | — | ⬜ |
| SF-021 | P1 | M | feat | `frontend/Dockerfile` multi-stage | — | ⬜ |
| SF-022 | P1 | M | refactor | `backend/pom.xml`: frontend plugin KALDIR | 020 | ⬜ |
| SF-023 | P1 | M | refactor | Kök `Dockerfile`: backend-only | 022 | ⬜ |
| SF-024 | P2 | M | test | Standalone docker build test | 021,022 | ⬜ |

### Epic 1.5.D — dev-full Compose

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-030 | P1 | M | feat | `docker-compose.dev-full.yml` (5 servis + JPDA) | 012,021 | ⬜ |
| SF-031 | P1 | S | chore | network + depends_on + healthcheck | 030 | ⬜ |
| SF-032 | P2 | M | test | Integration: routing + rate limit | 030,031 | ⬜ |
| SF-033 | P1 | S | chore | `vite.config.ts` proxy KORU | — | ⬜ |

### Epic 1.5.E — prod Compose

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-040 | P1 | M | refactor | `docker-compose-prod.yml` 3-container | 030 | ⬜ |
| SF-041 | P2 | S | feat | TLS cert path placeholder | 040 | ⬜ |
| SF-042 | P2 | S | chore | `.env.example`: nginx + JWT vars | — | ⬜ |

---

## Faz 2 — Kimlik Doğrulama & RBAC + Log + Yönetim

> Sıralama önemli — her alt-faz bir sonrakinin ön koşulu. **K-18 (2026-07-09):** Faz 1.5 (Nginx) ertelendi, doğrudan Faz 2'ye geçildi. Kullanıcı Faz 3 öncesi tam RBAC platformu istiyor (user CRUD, yetki atama, login/token, 3 katmanlı log, admin/user frontend). Backend-önceli sıralama: tüm Faz 2 backend (2.0→2.10) bitince Faz 4.0.B frontend gelir.

### Epic 2.0 — Foundation Refactors (user-role-demo-app'ten devşirme)

> Cross-cutting iyileştirmeler. Auth işinden **önce** yapılmalı — exception/error altyapısı, auditing bug fix, observability.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-167 | P0 | S | fix | **`DateTimeProvider` bean (UTC)** — `@CreatedDate` OffsetDateTime populate için BUG FIX (RISK-15) | — | ✅ |
| SF-163 | P0 | M | refactor | `ApiErrorResponse`/`ApiFieldError`/`ApiErrorFactory` (uniform error shape + traceId) | — | ⬜ |
| SF-164 | P0 | M | refactor | Exception hiyerarşisi: `BusinessException`→`AuthException`/`ResourceNotFoundException` + stable error codes | 163 | ⬜ |
| SF-171 | P2 | S | feat | `sanitizeRejectedValue` (validation error'da password/token maskeleme) | 163 | ⬜ |
| SF-165 | P1 | M | feat | `RequestLoggingFilter` + traceId (MDC) + `X-Request-Id` header + log pattern | — | ⬜ |
| SF-166 | P1 | S | feat | `PasswordEncodingListener` (JPA `@PrePersist`/`@PreUpdate`, şifre otomatik encode) | — | ⬜ |

### Epic 2.0.B — Critical Fixes (değerlendirme bulguları, 2026-07-09)

> Kod analizi sonucu keşfedilen P0 düzeltmeler. **2.9 User CRUD / 2.10 log'dan ÖNCE** çözülmeli.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-178 | P0 | M | feat | `TenantMigrationRunner` (`ApplicationRunner`) — startup'ta tüm `t_companies` şemalarını Flyway migrate (RISK-16). V2 tenant migration'ından ÖNCE gelmeli | — | ⬜ |
| SF-179 | P0 | M | fix | UNIQUE → **partial index** (`WHERE is_deleted=false`): username/email + role/perm/group name. Tenant template + mevcut tenant'larda (RISK-17). User CRUD'dan ÖNCE | 178 | ⬜ |
| SF-180 | P0 | S | fix | `hashCode()` düzelt — hem `BaseEntity` hem `GeneratedIdAuditEntity` (DEBT-7). RBAC (Set<Permission> vb.)'dan ÖNCE | — | ⬜ |
| SF-181 | P1 | M | feat | `TaskDecorator` — TenantContext + SecurityContext propagation (`@Async`, RISK-10). Audit/email async için | — | ⬜ |

### Epic 2.1 — MapStruct + DTO

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-050 | P1 | S | feat | `persistence/pom.xml`: mapstruct (sıralama KRİTİK) | — | ⬜ |
| SF-051 | P1 | S | feat | `MappingConfig` (@MapperConfig) | 050 | ⬜ |
| SF-052 | P1 | M | feat | DTO record'ları | — | ⬜ |
| SF-053 | P1 | M | feat | `AuthMapper` interface | 050,051 | ⬜ |
| SF-054 | P1 | S | refactor | `AuthController`: Map.of → DTO+mapper | 053,052 | ⬜ |
| SF-055 | P2 | S | test | MapStruct build test | 053 | ⬜ |

### Epic 2.3 — Spring Security Core ⚠️ (071-075 tek PR'da)

> ⚠️ `spring-boot-starter-security` tek başına default form login getirir, app'i kırar. Bu yüzden SF-071 + SF-072 + SF-073 + SF-074 + SF-075 **tek PR'da** commit edilmeli.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-070 | P1 | ? | spike | BCrypt migration stratejisi (RISK-13) | — | ⬜ |
| SF-071 | P0 | S | feat | `pom.xml`: spring-boot-starter-security | — | ⬜ |
| SF-072 | P0 | L | feat | `SecurityConfig`: filterChain+STATELESS+CSRF | 071 | ⬜ |
| SF-073 | P0 | S | feat | `BCryptPasswordEncoder(12)` | 070,071 | ⬜ |
| SF-074 | P0 | M | feat | `CorsConfig`: CorsConfigurationSource | 071 | ⬜ |
| SF-075 | P0 | S | feat | JSON 401/403 handlers (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) — `ApiErrorFactory` ile uniform shape + traceId | 072 | ⬜ |
| SF-076 | P1 | M | test | Security smoke (401 + permitAll) | 072,075 | ⬜ |

### Epic 2.4 — JWT Infrastructure (oauth2-resource-server + RSA)

> jjwt yerine `spring-boot-starter-oauth2-resource-server` (Nimbus). RSA asimetrik imzalama (RS256). `oauth2ResourceServer().jwt()` auto-config filter **AKTİF EDİLMEZ** (RISK-14) — custom `JwtAuthenticationFilter` gerekli (tokenInvalidBefore check için).

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-090 | P1 | S | feat | `pom.xml`: `oauth2-resource-server` (jjwt DEĞİL — Spring native Nimbus) | — | ⬜ |
| SF-091 | P1 | S | feat | `RsaKeyProperties` record + `certs/*.pem` + `.gitignore` + openssl keygen docs | — | ⬜ |
| SF-092 | P1 | M | feat | `JwtConfig` (JwtEncoder/JwtDecoder bean, RS256) + `JwtTokenProvider` (Spring JwtClaimsSet) | 090,091 | ⬜ |
| SF-093 | P1 | S | test | JwtTokenProvider unit test (üret→decode→claims) | 092 | ⬜ |
| SF-094 | P1 | M | feat | `CustomUserDetails` | — | ⬜ |
| SF-095 | P1 | M | feat | `CustomUserDetailsService` (tenant-aware, Group→Role→Permission) | 094 | ⬜ |
| SF-168 | P0 | S | feat | `tokenInvalidBefore` field `UserAccount`'a + Flyway tenant migration | — | ⬜ |
| SF-096 | P0 | M | feat | `JwtAuthenticationFilter` (cookie→decode→Redis blacklist check+perms→DB `tokenInvalidBefore`→SecurityContext) | 072,092,095,112,168 | ⬜ |
| SF-097 | P0 | S | feat | SecurityConfig'e filter hook (`.oauth2ResourceServer()` ÇAĞIRMA — RISK-14) | 072,096 | ⬜ |
| SF-098 | P1 | M | test | Filter integration test (cookie→auth, yok→401, revoked→401) | 097 | ⬜ |

### Epic 2.6 — Redis

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-110 | P1 | M | feat | `RedisConfig`: RedisTemplate+serializer | — | ⬜ |
| SF-111 | P1 | S | test | RedisConfig connection test | 110 | ⬜ |
| SF-112 | P1 | M | feat | `TokenBlacklistService` | 110 | ⬜ |
| SF-113 | P1 | S | test | TokenBlacklist unit test | 112 | ⬜ |
| SF-114 | P1 | M | feat | `PermissionCacheService` (TTL 10dk) | 110 | ⬜ |
| SF-115 | P1 | S | test | PermissionCache unit test | 114 | ⬜ |

### Epic 2.5 — Auth Endpoints

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-130 | P0 | L | feat | `AuthService.login()` iş mantığı | 092,095,110 | ⬜ |
| SF-131 | P0 | M | feat | LoginRequest/LoginResponse DTO | 052 | ⬜ |
| SF-132 | P0 | M | feat | `POST /login`: Set-Cookie + RefreshToken DB | 130,131 | ⬜ |
| SF-133 | P0 | M | test | Login integration test | 132 | ⬜ |
| SF-134 | P1 | M | feat | `POST /refresh` | 132 | ⬜ |
| SF-135 | P1 | S | test | Refresh test | 134 | ⬜ |
| SF-136 | P1 | M | feat | `POST /logout`: **Redis blacklist (current access token, granular)** + RefreshToken revoke. `tokenInvalidBefore` KULLANMA (multi-device korunsun) | 132,112 | ⬜ |
| SF-137 | P1 | S | test | Logout test | 136 | ⬜ |
| SF-138 | P1 | L | feat | `POST /register`: email domain + User | 130 | ⬜ |
| SF-139 | P1 | M | test | Register test | 138 | ⬜ |
| SF-140 | P2 | S | feat | `GET /me` | 132 | ⬜ |
| SF-169 | P0 | M | feat | Refresh token rotation + reuse detection (ihlal→tüm token revoke + `tokenInvalidBefore`) | 132 | ⬜ |
| ~~SF-141~~ | ~~P2~~ | ~~S~~ | ~~feat~~ | ~~JwtAuthFilter blacklist hook~~ — **KALDIRILDI** (`tokenInvalidBefore` ile gereksiz) | — | ❌ |

### Epic 2.7-2.8 — Wrap-up

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-160 | P1 | S | fix | `AuditorAware` SecurityContext userId (RISK-3) | 132 | ⬜ |
| SF-170 | P1 | L | feat | Bootstrap data initializer (rol/permission/group seed, idempotent, diff-based) | 076 | ⬜ |
| SF-161 | P1 | S | feat | springdoc-openapi dep | — | ⬜ |
| SF-162 | P2 | S | feat | Swagger scheme doc + profile gating | 161,132 | ⬜ |

### Epic 2.9 — User & RBAC Management (K-18, kullanıcı talebi)

> User CRUD + yetki atama/silme + user page + rol-bazlı panel. **Backend kısmı** (frontend karşılığı Epic 4.0.B'de). `@EnableMethodSecurity` + `@PreAuthorize` method-level yetkilendirme. Permission namespace `{module}:{resource}:{action}`.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-142 | P0 | M | feat | `UserController` (`/api/v1/users`: sayfalı list / GET{id} / POST / PUT{id} / DELETE{id}) + DTO | 053 | ⬜ |
| SF-143 | P0 | M | feat | `UserService` (`@Transactional`, soft-delete, account+profile cascade) | 142 | ⬜ |
| SF-144 | P1 | M | feat | `UserProfileController` (`PUT /users/me/profile`) — "bilgi ekleme" (firstName/phone/adres) | 142 | ⬜ |
| SF-145 | P1 | M | feat | Email doğrulama akışı (`POST /auth/verify-email` token üret + `/confirm`) — alan entity'de hazır | 138 | ⬜ |
| SF-146 | P1 | S | feat | Password reset akışı (`POST /auth/password-reset` + `/confirm`) — alan entity'de hazır | 138 | ⬜ |
| SF-152 | P1 | S | feat | `GET /auth/me` (mevcut kullanıcı + permission/rol listesi) | 132 | ⬜ |
| SF-147 | P1 | M | feat | `RoleController` (`/api/v1/roles`: CRUD + `POST/DELETE /roles/{id}/permissions`) | 170 | ⬜ |
| SF-148 | P1 | M | feat | `PermissionController` (`/api/v1/permissions`: list/CRUD) | 170 | ⬜ |
| SF-149 | P1 | M | feat | `GroupController` (`/api/v1/groups`: CRUD + `POST/DELETE /groups/{id}/roles`) | 170 | ⬜ |
| SF-150 | P0 | M | feat | User-role/group atama (`POST/DELETE /users/{id}/roles`, `/users/{id}/groups`) + `PermissionCacheService` evict | 147,114 | ⬜ |
| SF-151 | P0 | M | feat | `@PreAuthorize` tüm admin endpoint'lerine (örn. `@PreAuthorize("hasAuthority('iam:user:write')")`) | 150 | ⬜ |
| SF-153 | P1 | M | test | User & RBAC integration test (CRUD + rol atama + tenant izolasyonu + permission red) | 151 | ⬜ |

### Epic 2.10 — Audit & Logging (K-19, 3 katmanlı log)

> Audit log + giriş geçmişi + request/trace log. Her birinin **kendi tablosu + endpoint'i** (frontend sayfaları Epic 4.0.B'de). Request/trace log altyapısı SF-165 (Epic 2.0) ile gelir; burada görüntüleme/arama eklenir. Yeni tenant migration `tenant/V2__audit_login_history.sql`.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-172 | P0 | M | feat | `tenant/V2__audit_login_history.sql`: `t_audit_logs` (actor/action/entity/old-new JSONB/ip/trace_id) + `t_login_history` (user/success/ip/user_agent/reason) | — | ⬜ |
| SF-173 | P0 | S | feat | `AuditLog` + `LoginHistory` entity + repository (tenant şeması) | 172 | ⬜ |
| SF-174 | P1 | M | feat | `AuditService` + AOP `@AuditLog` annotation (admin aksiyonları otomatik yakala: user/rol create/delete/assign) | 173 | ⬜ |
| SF-175 | P0 | S | feat | Login history yazımı — login/refresh/register/logout'ta success+failure kaydı | 173,132 | ⬜ |
| SF-176 | P1 | M | feat | Görüntüleme endpoint'leri: `GET /audit-logs`, `GET /login-history`, `GET /request-logs` (admin `@PreAuthorize`, sayfalı + filtre) | 174,175,151 | ⬜ |
| SF-177 | P2 | S | feat | Request log arama — traceId ile lookup (SF-165 MDC traceId üzerinden) | 165,176 | ⬜ |

---

## Faz 3 — Modüler Platform (Module System + Built-in Modüller)

> **Vizyon değişikliği (2026-07-09):** Eski Faz 3 sadece sabit `Project`/`Task` içeriyordu. Artık hibrit modüler platform: önce Module System altyapısı (3.0), sonra built-in modüller (3.1-3.4). Custom App Builder backend altyapısı da 3.0 ile gelir; UI'sı Faz 4.2.

### Epic 3.0.A — Module System & Plan/Subscription

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-200 | P0 | M | feat | `public/V2__plan_subscription_modules.sql`: `t_plans`, `t_subscriptions`, `t_tenant_modules`, `t_module_catalog` | — | ⬜ |
| SF-201 | P0 | S | feat | `Plan`, `Subscription`, `TenantModuleActivation`, `ModuleCatalog` entity'leri | 200 | ⬜ |
| SF-202 | P0 | M | feat | `Module` registry (enum/konfig — key, name, min_plan, flyway_path) | — | ⬜ |
| SF-203 | P0 | L | feat | `ModuleActivationService` (plan kontrol→Flyway tenant migration→permission seed→kayıt) | 200,201,202 | ⬜ |
| SF-204 | P1 | M | feat | `PermissionSeeder` — modül aktivasyonunda `{module}:{resource}:{action}` namespace insert | 203 | ⬜ |
| SF-205 | P1 | S | feat | Tenant signup → `t_subscriptions` (default FREE) + varsayılan modüller (Tasks+Notes) | 203 | ⬜ |
| SF-206 | P2 | M | test | Module activation integration test (plan reject, Flyway, permission seed) | 203 | ⬜ |

### Epic 3.0.B — Custom App Builder Backend (Notion-style, K-15)

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-210 | P0 | M | feat | `tenant/V2__app_builder.sql`: `t_apps`, `t_app_properties`, `t_app_records`, `t_app_record_values(value JSONB)`, `t_app_views` | — | ⬜ |
| SF-211 | P0 | S | feat | `persistence/pom.xml`: hypersistence-utils + `App*` entity'leri (JSONB mapping) | 210 | ⬜ |
| SF-212 | P0 | L | feat | `AppBuilderService`: app/property/record/view CRUD + property type validation | 211 | ⬜ |
| SF-213 | P1 | M | feat | Property type validators (TEXT/NUMBER/SELECT/DATE/USER/RELATION/FORMULA) | 212 | ⬜ |
| SF-214 | P1 | M | feat | `AppRecordValueRepository` — JSONB GIN index ile sorgu (filter/sort) | 212 | ⬜ |
| SF-215 | P1 | M | feat | Limit enforcement (max_custom_apps, max_records_per_app — soft-block) | 212 | ⬜ |
| SF-216 | P2 | M | test | Custom app builder CRUD test (app→property→record→view akışı) | 212 | ⬜ |
| SF-217 | P2 | S | spike | View config güvenliği (filter/formula expression injection — sandbox/AST validation) | 212 | ⬜ |

### Epic 3.0.C — Module/App API

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-220 | P1 | M | feat | `GET /api/v1/modules` (katalog + aktif) + `POST /modules/{key}/activate` | 203 | ⬜ |
| SF-221 | P1 | L | feat | `GET/POST/PATCH/DELETE /api/v1/apps` (custom app CRUD) | 212 | ⬜ |
| SF-222 | P1 | L | feat | `GET/POST/PATCH/DELETE /api/v1/apps/{id}/records` + `/properties` + `/views` | 212 | ⬜ |
| SF-223 | P2 | M | feat | MapStruct mappers (`AppMapper`, `RecordMapper`, `ViewMapper`) | 051,212 | ⬜ |

### Epic 3.1 — Built-in "Tasks" Modülü

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-230 | P1 | M | feat | `tenant/V3__module_tasks.sql`: `t_tasks`, `t_task_comments` | — | ⬜ |
| SF-231 | P1 | M | feat | `Task`, `TaskComment` entity + repository | 230 | ⬜ |
| SF-232 | P1 | L | feat | `TaskService` + `TaskController` (`/api/v1/tasks`) + `@PreAuthorize('tasks:task:*')` | 231,204 | ⬜ |
| SF-233 | P2 | M | feat | Kanban board view API (group by status) | 232 | ⬜ |
| SF-234 | P2 | M | test | Tasks CRUD + permission isolation test | 232 | ⬜ |

### Epic 3.2 — Built-in "Notes" Modülü

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-240 | P2 | M | feat | `tenant/V4__module_notes.sql`: `t_notes`, `t_note_categories` | — | ⬜ |
| SF-241 | P2 | M | feat | `Note`, `NoteCategory` entity + service + controller (`/api/v1/notes`) | 240,204 | ⬜ |
| SF-242 | P3 | S | feat | Arama + kategori filtreleme | 241 | ⬜ |

### Epic 3.3 — Built-in "Warehouse" Modülü

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-250 | P2 | L | feat | `tenant/V5__module_warehouse.sql`: `t_products`, `t_warehouses`, `t_stock_items`, `t_stock_movements` | — | ⬜ |
| SF-251 | P2 | L | feat | Entity'ler (Product/Warehouse/StockItem/StockMovement) + service + controller | 250,204 | ⬜ |
| SF-252 | P3 | M | feat | Stok hareketleri (IN/OUT/TRANSFER) + minimum stok uyarısı | 251 | ⬜ |

### Epic 3.4 — Built-in "Logistics" Modülü

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-260 | P2 | L | feat | `tenant/V6__module_logistics.sql`: `t_shipments`, `t_vehicles`, `t_drivers`, `t_routes` | — | ⬜ |
| SF-261 | P2 | L | feat | Entity'ler (Shipment/Vehicle/Driver/Route) + service + controller | 260,204 | ⬜ |
| SF-262 | P3 | M | feat | Sevkiyat durum makinesi (CREATED→IN_TRANSIT→DELIVERED) | 261 | ⬜ |

### Epic 3.X — Testcontainer + Rate Limit

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-270 | P0 | L | test | Testcontainers: tenant signup + modül aktivasyon + CRUD + **veri izolasyonu** e2e | 232 | ⬜ |
| SF-271 | P2 | M | feat | Rate limiting (Redis, IP + tenant bazlı) | 110 | ⬜ |

---

## Faz 4 — Frontend (Modüler UI + Custom App Builder UI)

> 4.0 core frontend, 4.1 built-in modül UI'ları, 4.2 Custom App Builder UI (Notion-style — en iddialı).

### Epic 4.0 — Frontend Core

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-300 | P1 | M | chore | Bağımlılıklar (TanStack Query, Zustand, Tailwind, react-router) | — | ⬜ |
| SF-301 | P1 | M | feat | Tailwind setup + klasör mimarisi | 300 | ⬜ |
| SF-302 | P1 | L | feat | Auth UI (login/register) + Zustand auth store + Axios interceptor | 300 | ⬜ |
| SF-303 | P1 | M | feat | **Modül-bazlı sidebar** (`GET /modules` → aktif modüller) + aktivasyon ekranı | 220 | ⬜ |
| SF-304 | P2 | M | refactor | `App.tsx` parçala + React Router route yapısı | 301 | ⬜ |

### Epic 4.0.B — Admin/User/Log Management UI (K-20, Faz 3 öncesi)

> **Backend Faz 2 tamamen bitince gelir** (backend-öncesi sıralama, K-18). Faz 4 core stack (SF-300/301/302/304) burada kurulur. Tenant-scoped: her şirket kendi verisini görür (izolasyon backend'de). Built-in modül UI'ları (Tasks/Notes vb.) hâlâ Epic 4.1'de kalır.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-330 | P1 | M | feat | Login/Register sayfaları + Zustand auth store + axios interceptor (`withCredentials` cookie) | 300,302 | ⬜ |
| SF-331 | P1 | M | feat | React Router + auth guard (rol/permission bazlı route koruma) | 304 | ⬜ |
| SF-332 | P1 | L | feat | Admin panel — **User management** UI (CRUD + rol/grup atama) | 150,331 | ⬜ |
| SF-333 | P1 | L | feat | Admin panel — **Role/Permission/Group yönetimi** UI | 147,148,149,331 | ⬜ |
| SF-334 | P1 | M | feat | **User page** — profil düzenleme + email doğrulama durumu + kendi login geçmişi | 144,152,331 | ⬜ |
| SF-335 | P1 | M | feat | **Audit log sayfası** (filtreli tablo: actor/action/entity/tarih) | 176,331 | ⬜ |
| SF-336 | P1 | M | feat | **Login history sayfası** (user/başarı/IP/tarih filtre) | 175,331 | ⬜ |
| SF-337 | P1 | M | feat | **Request log sayfası** (traceId arama + seviye filtre) | 165,177,331 | ⬜ |
| SF-338 | P1 | S | feat | Rol-bazlı sidebar (permission'a göre menü göster/gizle — admin panel sadece yetkililere) | 152,331 | ⬜ |

### Epic 4.1 — Built-in Modül UI'ları

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-310 | P2 | L | feat | Tasks UI (liste + Kanban board) | 232,304 | ⬜ |
| SF-311 | P3 | M | feat | Notes UI (rich-text editör + kategori) | 241,304 | ⬜ |
| SF-312 | P3 | L | feat | Warehouse UI (ürün/stok tablosu + hareketler) | 251,304 | ⬜ |
| SF-313 | P3 | L | feat | Logistics UI (sevkiyat listesi + durum güncelleme) | 261,304 | ⬜ |

### Epic 4.2 — Custom App Builder UI (Notion-style — en iddialı)

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-320 | P2 | M | feat | App designer sihirbazı (isim/ikon/açıklama) | 221,304 | ⬜ |
| SF-321 | P2 | L | feat | Property editor (tip seçimi + config — select options/relation/formula) | 221,320 | ⬜ |
| SF-322 | P2 | L | feat | Record editör (property type'a göre input widget'ları) | 222,320 | ⬜ |
| SF-323 | P2 | L | feat | TABLE view renderer (kolon=property, satır=record) | 222,322 | ⬜ |
| SF-324 | P2 | L | feat | BOARD view renderer (Kanban — group_by) | 222,322 | ⬜ |
| SF-325 | P3 | M | feat | CALENDAR view renderer (date property'sine göre) | 222,322 | ⬜ |
| SF-326 | P3 | M | feat | GALLERY + LIST view renderer | 222,322 | ⬜ |
| SF-327 | P3 | L | feat | Filter/sort/group_by config UI (drag-drop, expression editor) | 323 | ⬜ |
| SF-328 | P3 | M | feat | Relation picker (başka app lookup) | 322 | ⬜ |
| SF-329 | P3 | S | feat | Plan limit göstergesi (kalan quota) | 303 | ⬜ |

---

## Faz 5 — Hardening & Operasyon

> 1. [ ] **TLS termination:** `nginx.conf` Let's Encrypt (certbot) veya external certs. HTTP → HTTPS redirect. HSTS header.
> 2. [ ] **Observability:** `spring-boot-starter-actuator` + Micrometer → Prometheus metrics. `management.endpoints.web.exposure.include=health,info,metrics,prometheus`. `management.server.port=9090` (internal). OpenTelemetry tracing **ertelendi**.
> 3. [ ] **CI/CD (GitHub Actions):** `.github/workflows/ci.yml` — PR'da `mvn test` + `npm run lint` + `npm run build`. main push → Docker build + push registry. Secrets: GitHub Actions secret store.
> 4. [ ] **Ertelenen kararlar değerlendir:** OAuth2 sosyal giriş, WebSocket/SSE, S3/MinIO, OpenTelemetry, microservice geçişi.

---

## Faz 6 — Billing & Abonelik Yönetimi (K-16'nın tamamlanması)

> Plan bazlı modül aktivasyonunun (K-16) finansal tarafı. Faz 3.0'da plan yapısı tanımlı; Faz 6 gerçek ödeme + plan yönetim akışını getirir.

| ID | P | ⏱ | Type | Title | Deps | Durum |
|----|---|---|---|---|---|---|
| SF-400 | P2 | ? | spike | Ödeme sağlayıcı seçimi (Stripe vs iyzico — Türkiye pazarı) | — | ⬜ |
| SF-401 | P2 | L | feat | Ödeme sağlayıcı entegrasyonu + webhook dinleme | 400 | ⬜ |
| SF-402 | P2 | L | feat | Plan upgrade/downgrade akışı (soft-block modül/limit yönetimi) | 401,203 | ⬜ |
| SF-403 | P3 | M | feat | Invoice/fatura yönetimi + PDF | 401 | ⬜ |
| SF-404 | P3 | S | feat | Trial period (14gün PRO trial yeni tenant'lara) | 402 | ⬜ |
| SF-405 | P3 | M | feat | Platform admin dashboard (MRR, churn, tenant istatistikleri) | 401 | ⬜ |

---

## İstatistik & Kritik Yol

### Faz 1.5-2 istatistik

| Epic | Ticket | P0 | L | Spike |
|---|---|---|---|---|
| 1.5.A @Transactional | 4 | 2 | 0 | 0 |
| 1.5.B Nginx | 4 | 0 | 0 | 0 |
| 1.5.C Frontend Docker | 5 | 0 | 0 | 1 |
| 1.5.D dev-full | 4 | 0 | 0 | 0 |
| 1.5.E prod | 3 | 0 | 0 | 0 |
| 2.0 Foundation Refactors | 6 | 3 | 0 | 0 |
| 2.0.B Critical Fixes | 4 | 3 | 0 | 0 |
| 2.1 MapStruct | 6 | 0 | 0 | 0 |
| 2.3 Security | 7 | 5 | 1 | 1 |
| 2.4 JWT (oauth2-resource-server) | 10 | 4 | 0 | 0 |
| 2.6 Redis | 6 | 0 | 0 | 0 |
| 2.5 Auth | 12+1 | 5 | 2 | 0 |
| 2.7-2.8 | 4 | 0 | 1 | 0 |
| 2.9 User & RBAC Management | 12 | 4 | 0 | 0 |
| 2.10 Audit & Logging | 6 | 3 | 0 | 0 |
| **Toplam** | **93** (+1 ❌) | **29** | **4** | **2** |

> **Faz 1.5 (1.5.A-E, 20 ticket) ERTELENDİ** (K-18) — toplamda sayılır ama Faz 2 sonrasına atıldı. `@Transactional` (1.5.A, SF-001~004) Faz 2.2'de uygulanır. Gerçek "aktif Faz 2" ticket sayısı: 93 − 20 = **73**.

### Faz 3-6 istatistik

| Epic | Ticket aralığı | P0 | L |
|---|---|---|---|
| 3.0.A Module System | SF-200~206 | 4 | 1 |
| 3.0.B Custom App Builder Backend | SF-210~217 | 3 | 2 |
| 3.0.C Module/App API | SF-220~223 | 0 | 2 |
| 3.1 Tasks | SF-230~234 | 0 | 2 |
| 3.2 Notes | SF-240~242 | 0 | 0 |
| 3.3 Warehouse | SF-250~252 | 0 | 2 |
| 3.4 Logistics | SF-260~262 | 0 | 2 |
| 3.X Testcontainer+RateLimit | SF-270~271 | 1 | 1 |
| 4.0 Frontend Core | SF-300~304 | 0 | 2 |
| 4.0.B Admin/User/Log UI | SF-330~338 | 0 | 2 |
| 4.1 Built-in UI | SF-310~313 | 0 | 3 |
| 4.2 App Builder UI | SF-320~329 | 0 | 5 |
| 6.0 Billing | SF-400~405 | 0 | 3 |
| **Faz 3-6 toplam** | **SF-200~405** | **8** | **27** |

**Toplam (Faz 1.5-6):** 93 + 72 = **165 ticket**, **37 P0**, **31 L**.

### Kritik Yol (P0 zinciri — K-18)

**Faz 2:** SF-167 (DateTimeProvider bug) → **SF-178/179/180 (multi-tenancy/UNIQUE/hashCode fixes)** → SF-163/164 (error altyapısı) → SF-071 → SF-072 → SF-073/074/075 → SF-168 (tokenInvalidBefore) → SF-096 → SF-097 → SF-130 → **SF-132 (ilk çalışan login ⭐)** → SF-150/151 (RBAC yönetimi) → SF-172/176 (log) → SF-330+ (UI).

**Faz 3 başlangıcı:** SF-200 → SF-203 → SF-205 (signup+plan) → SF-210/211/212 (App Builder) → SF-232 (Tasks çalışan) → **SF-270 (izolasyon testi ⭐)**.

**Bağımsız başlanabilir (paralel):** SF-001, SF-010, SF-050, SF-090, SF-110, SF-161, **SF-167 (P0 bug fix), SF-165 (logging), SF-166 (PasswordEncodingListener)**.

**En uzun iş (paralel planlama için):** SF-203 (ModuleActivationService), SF-212 (AppBuilderService), SF-321/322/323/324 (App Builder UI), SF-402 (plan upgrade akışı).
