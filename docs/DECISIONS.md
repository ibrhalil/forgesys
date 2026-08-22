# Karar Kayıtları (Decision Log)

> Bu dosya ForgeSys'un mimari/teknik kararlarının (K-XX), risk kayıtlarının (RISK-XX) ve teknik borçlarının (DEBT-XX) tek merkezi. ID'ler karar verildiği sırayla artar, değişmez. Kararlar ticket numarasına değil, bağlam+gerekçe+etki'ye bağlıdır.

## Format

Her kayıt:
- **Bağlam:** Hangi problem/ikilem
- **Karar:** Ne seçildi, neden
- **Durum:** Uygulandı / Planlandı / İptal
- **Etki:** Ne değişti, nelere dikkat

---

## Mimari Kararlar (K-XX)

### K-15
**Custom App Builder (Notion-style)**
- **Bağlam:** Tenant'ların kendi ihtiyaçlarına özel mini-uygulamalar yaratması (Notion/Airtable mantığı) gerekiyor. Sadece sabit built-in modüllere değil, esnek veri modeline ihtiyaç var.
- **Karar:** JSONB EAV modeliyle tenant custom app'leri desteklenir. `t_apps`, `t_app_properties`, `t_app_records`, `t_app_record_values(value JSONB)`, `t_app_views`. Property tipleri: TEXT/NUMBER/SELECT/DATE/USER/RELATION/FORMULA.
- **Durum:** Planlandı (Faz 3.0.B).
- **Etki:** Hibrit ürün modeli: built-in modüller (Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı).

### K-16
**Plan Bazlı Modül Aktivasyonu**
- **Bağlam:** Tüm tenant'lar tüm modülleri kullanmamalı. Free/Pro/Enterprise planları modül erişimini belirlemeli.
- **Karar:** `t_plans`, `t_subscriptions`, `t_tenant_modules` yapısı (public `V2`). Tenant signup -> varsayılan FREE + default modüller (Tasks+Notes; bugün `pm`, `forgesys.modules.default-keys`). Modül aktivasyonu plan kontrolü + Flyway tenant migration + permission seed adımlarından oluşur.
- **Durum:** UYGULANDI (2026-08-22, Faz 3.0.A backend çekirdeği). Finansal tarafı (gerçek ödeme, plan değişimi, deaktivasyon) Faz 6.
- **Etki / uygulama kararları:**
  - **Modül registry'si kodda, DB'de değil:** `t_module_catalog` tablosu İÇİNMEZ — `ModuleDefinition` enum'u (key/displayName/minPlan/ownMigrations/permissions) tek doğruluk kaynağı; `t_tenant_modules` yalnızca aktivasyon durumu taşır (`module_key` string). Bir modül koddur (entity/service/migration); registry kaydı kodla birlikte gitmeli, DB'den sapmasın.
  - **Plan registry de kodda:** `PlanDefinition` (FREE/PRO/ENTERPRISE + rank) — `PlanSyncRunner` (`@Order(0)`, `!test`) `t_plans`'a idempotent upsert. Plan rank karşılaştırması DB satırı üzerinden (`plan.rank >= module.minPlan.rank()`).
  - **Modül-başı ayrı Flyway history:** modül migration'ları `db/migration/module/<key>/` altında (core `tenant/` DIŞINDA — Flyway location taraması rekürsif olduğundan core ağacında kalsaydı core history'ye dahil olurdu — IT'de keşfedildi) + history tablosu `flyway_schema_history_mod_<key>` (her modül V1'den bağımsız versiyonlanır, core ile çakışma imkânsız). `baselineOnMigrate(true) + baselineVersion("0")` — modül ilk aktivasyonda non-empty schema üstünde history açar, hiçbir migration atlamaZ (core'daki K-36 baseline-yasağı modül history'sini etkilemez).
  - **Transaction split (FK-deadlock önleme):** aktivasyon kaydı (`t_tenant_modules`) caller tx'ine KATILIR (provisioning outer tx'i commit edilmemiş `Company` satırını tutar — REQUIRES_NEW insert PG'de FK lock ile self-deadlock yapardı); yalnızca permission seed `REQUIRES_NEW` (tenant şema yazısı, outer session `public`'a pinned — RISK-26). Gerçek PG'de doğrulandı (`ModuleActivationIT`).
  - **Provisioning hook:** `verifyAndProvision` → FREE subscription insert + `activateDefaultModules` (default keys). `ModuleSyncRunner` (`!test`) startup'ta mevcut tenantlara FREE backfill + default modüller + aktif modüllerin migration/permission re-sync'i (yeni ship edilen modül migration'ları mevcut tenantlara yayılır).
  - **PermissionCatalog split:** `ALL` → `CORE` (iam:* + platform:* + yeni `iam:module:read/write`); modül permission'ları (`pm:*`) `ModuleDefinition` sahipliğinde, aktivasyonda seed edilir. Admin (all_permissions) modül permission'larına otomatik ulaşır.

### K-18
**Nginx ertelendi, Faz 2 önceli (2026-07-09)**
- **Bağlam:** Orijinal plan Faz 1.5'te 3-container full separation + Nginx dev'de aktif idi. Kullanıcı Faz 3 öncesi tam RBAC platformu istiyor (user CRUD, yetki atama, login/token, 3 katmanlı log, admin/user frontend).
- **Karar:** Faz 1.5 (Nginx topology) Faz 2 sonrasına ertelendi. Doğrudan Faz 2'ye geçildi. Backend-önceli sıralama: tüm Faz 2 backend bitince Faz 4.0.B frontend gelir.
- **Durum:** Uygulandı (erteleme). **Güncelleme (2026-07-25, K-33):** Erteleme, Nginx topology planı netleştirilerek (K-33) **proje %90 tamamlanana kadar** uzatıldı. Karar gerekçesi hâlâ geçerli (backend-öncelik + Vite proxy dev'i karşılıyor).
- **Etki:** Vite proxy dev'de Nginx'in görevini görüyor; prod tek app container (`:8080` expose). Faz 1.5 ticketları (toplamda sayılır) pasif; K-33 topology'si ile birlikte uygulanacak.

### K-33
**Nginx Gateway Topology — Planlandı (uygulama %90 sonrasına erteli, 2026-07-25)**
- **Bağlam:** [K-18](#k-18) Faz 1.5'i ertelemişti; "VPS içinde Docker, birden fazla proje farklı portlarda, global Nginx tarafından yönetilen" shared-gateway topology'si isteniyor. Bu repo (ForgeSys) + ileride oluşturulacak diğer projeler aynı VPS'te host edilecek. Cloudflare gibi managed kolaylıklar **kullanılmayacak** (projenin kendini kanıtlama amacı taşıması). ForgeSys subdomain-based multi-tenancy (`*.forgesys.app`) wildcard TLS gerektiriyor.
- **Karar:** Aşağıdaki topology + TLS stratejisi **planlandı**; uygulama **proje %90 tamamlandıktan sonrasına erteli** (K-18 extension). Plan hazır, sadece execute bekliyor.
  1. **Topoloji — shared gateway, ayrı repo:** VPS'te `~/nginx-gateway/` ayrı bir git repo'da tek Nginx container. **External Docker network** (`gateway-net`) üzerinden tüm projelerin servislerine erişir. Her proje (ForgeSys dahil) compose'unda `ports:` → `expose:` (host port kapalı), `gateway-net`'e join, sabit container name. Yeni proje eklemek: compose'a network eklemek + gateway'e `conf.d/proje.conf` atmak.
  2. **TLS — Let's Encrypt + certbot, wildcard DNS-01:** Cloudflare yok. Wildcard `*.forgesys.app` için **DNS-01 challenge** (HTTP-01 wildcard desteklemez; dinamik tenant subdomain'leri tek-tek issue edilemez). Certbot DNS plugin (Cloudflare/Route53/DigitalOcean/Namecheap/Gandi — kullanılan DNS sağlayıcıya göre) + renewal cron. Certificate `infra/ssl/`'e (ForgeSys) veya Nginx container'ına konur. **Açık uç:** DNS sağlayıcı henüz net değil (K-33 uygulama anında netleştirilecek).
  3. **ForgeSys route (gateway'e kurulacak `infra/nginx/forgesys.conf`):** `server_name *.forgesys.app forgesys.app; proxy_pass http://forgesys-app:8080; proxy_set_header Host $host;` (Host header korunur — `TenantFilter` Host'tan subdomain çözümler). `/actuator/health` allow (healthcheck). Rate limit `/api/v1/auth/login` sıkı (RISK-22 ek katman).
  4. **Rate limit — ikisi birden:** Nginx `limit_req` (IP/path-bazlı) + varsa Cloudflare-like başka bir katman. Gerçek IP için Nginx `set_real_ip_from` + `real_ip_header` (Cloudflare yok, direkt client IP).
  5. **Security headers (Nginx):** HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy. **CSP Nginx'te değil** — backend `SecurityConfig`'te zaten var, duplicate/conflict önlenir.
  6. **HTTP→HTTPS redirect + HSTS:** Nginx `:80` → `:443` redirect (Cloudflare "Always Use HTTPS" gibi kolaylık yok, Nginx kendi yapar).
- **Durum:** PLANLANDI (uygulama %90 sonrası). K-18 ertelemesini detaylandırır + topology/TLS kararlarını sabitler.
- **Etki:**
  - Bu repo'da **şu an hiçbir dosya değişikliği yok** — plan notu olarak kaydedildi.
  - `docker-compose-prod.yml` `ports: "8080:8080"` geçici olarak expose'da kalır; K-33 uygulama anında `expose:` + `gateway-net`'e geçilir.
  - **Açık uçlar (uygulama anında netleşecek):** DNS sağlayıcı (certbot plugin seçimi), `nginx-gateway/` repo'su oluşturma, `infra/nginx/` şablonları, `infra/ssl/` cert yönetimi (renewal sonrası reload hook).
  - K-33 uygulandığında `infra/nginx/` altına: `nginx.conf` (gzip, rate-limit zone, real_ip), `snippets/proxy-common.conf`, `snippets/security-headers.conf`, `conf.d/_template.conf` gelecek.
  - `%90` ölçütü: Faz 2 (audit/log dahil) + Faz 3 (modüler platform + app builder backend) + Faz 4 (frontend) ana akışları; Faz 5 (TLS/CI/CD/observability) + Faz 6 (billing) hâlâ bekliyor olabilir.

### K-34
**Redis Refresh Token + Rotation + Reuse Detection + Per-Session Logout — UYGULANDI**
- **Bağlam:** Epic 2.5/2.6. Access token kısa ömürlü (15dk) ve stateless; kullanıcı sık re-login yapmak zorunda. Uzun ömürlü refresh token + rotation gerekiyor. Mevcut `tokenInvalidBefore` user-scoped (tüm cihazlar) — tek cihaz logout (per-session) mümkün değil. Dead `RefreshToken` entity/tablosu (`t_refresh_tokens`, tenant şeması, plaintext) Epic 2.5 için bırakılmıştı. Redis altyapısı (starter + config + container) hazır ama hiç bean yoktu.
- **Karar:** Opaque refresh token + Redis-first depolama + rotation + reuse detection:
  1. **Format — opaque + hash-at-rest.** Refresh token 32-byte URL-safe random; Redis'e **SHA-256 hash** olarak yazılır (RISK-30 felsefesi — store/backup leak replay edilemez). JWT değil (revocability). `t_refresh_tokens` tablosu **kullanılmaz** (dead kalır, churn önlenir); Flyway migration YOK.
  2. **Depolama — Redis hash + per-user index.** Token kaydı `refresh:tok:{hash}` (Redis hash: state/userId/email/tenant/issuedAt), TTL = refresh ömrü. Per-user index set `refresh:idx:{tenant}:{userId}` → `revokeAllForUser` için.
  3. **Rotation + reuse detection — atomik Lua.** `rotate` sadece `ACTIVE` token'ı `ROTATED`'e çevirip yeni token üretir (atomic conditional Lua script — concurrent race kapalı). Zaten `ROTATED` (consume edilmiş) token tekrar sunulursa → **REUSE**: tüm kullanıcı refresh token'ları revoke + `tokenInvalidBefore` set (access token'lar da ölür) → `auth_refresh_token_reuse` (401). Bilinen UX sınırı: aynı token'la eşzamanlı iki refresh (grace window yok) reuse tetikler — client refresh'i serialize etmeli (standart SPA pratiği). Grace window erteli.
  4. **Transport — ayrı cookie.** `sf_refresh_token` httpOnly cookie, `Path=/api/v1/auth` (sadece auth endpoint'lerine gönderilir), Secure (prod), SameSite=Lax. Body fallback (API client'lar, `RefreshRequest`).
  5. **Endpoint — `POST /api/v1/auth/refresh`** (permitAll; tenant TenantFilter'dan gelir, yeni access token aynı tenanta bound — RISK-19). Authorities DB'den **re-resolve** edilir (taze yetkiler + locked/disabled re-check; locked hesap refresh edemez — RISK-22 iyileştirmesi).
  6. **Per-session logout — jti blacklist.** Access token'a `jti` (JWT ID) claim eklendi. Logout: refresh consume + mevcut access `jti` Redis blacklist (`bl:jti:{jti}`, TTL = access ömrü). `JwtAuthenticationFilter` blacklist'i de kontrol eder → granular tek-token revoke. **Logout artık `tokenInvalidBefore` set ETMEZ** (o password change/reset/reuse için nuclear option). Diğer cihazlar çalışmaya devam eder.
  7. **Soyutlama + test stratejisi.** `RefreshTokenStore` + `TokenBlacklistService` interface'leri, `@Profile("!test")` Redis impl + `@Profile("test")` InMemory impl (`InMemoryVerificationSender` pattern'i). Default H2 build Docker'sız yeşil; gerçek Redis doğrulaması gated `RedisRefreshTokenIT` (`-Dforgesys.redis.it=true`, `GenericContainer("redis:7.4-alpine")` + `@DynamicPropertySource` — yeni dependency YOK, testcontainers-core zaten var).
- **Durum:** UYGULANDI (2026-07-30). 206 test yeşil (H2, 2 gated skip). PermissionCacheService (Epic 2.6'nın 3. parçası) **ertelendi** — yetkiler JWT'ye gömülü, cache sadece login/refresh mint'i optimize eder (düşük değer). K-28 session management (aktif session listesi, remote revoke endpoint'leri) bu altyapının üstüne gelir.
- **Etki:**
  - **`RedisConfig` yok:** Store'lar auto-config `StringRedisTemplate` kullanır (Redis hash + Lua). `GenericJackson2JsonRedisSerializer` (Jackson 2) projedeki Jackson 3 ile uyumsuz (NoClassDefFoundError) — JSON serializer gereksizdi (string/hash depolama yeterli). PermissionCacheService gelirse Jackson 3 uyumlu bir serializer ile yeniden değerlendirilir.
  - **Per-request maliyet:** +1 Redis lookup (jti blacklist) mevcut DB lookup'a (tokenInvalidBefore) eklendi. Redis in-memory → tolere edilebilir. `tokenInvalidBefore`'ı Redis cache'lemek erteli.
  - **Password change/reset artık refresh'leri de revoke eder** (`UserService.invalidateTokens` → `revokeAllForUser`) — çalınan refresh, şifre değişince yeni access mint edemez (yeni access'in iat > tokenInvalidBefore olsa bile).
  - **Logout davranış değişti:** per-session (tek cihaz). Eski user-scoped `tokenInvalidBefore` logout artık password change/reset/reuse'e özel.
  - **RISK-21 genişletildi:** granular tek-token (jti blacklist) + user-scoped (tokenInvalidBefore) iki katmanlı revoke.
  - **K-28 (session management) unblocked:** Redis session altyapısı hazır; `/users/me/sessions` endpoint'leri + `t_sessions_log` sonraki adım.
  - **Config:** `jwt.refresh-token-ttl-days` (default 7), `jwt.refresh-cookie-name` (`sf_refresh_token`), `jwt.refresh-cookie-secure` (prod `true`), `jwt.refresh-cookie-path` (`/api/v1/auth`) → `JwtCookieProperties` record'una eklendi. Yeni `ErrorCode`: `AUTH_REFRESH_TOKEN_INVALID` / `AUTH_REFRESH_TOKEN_REUSE`.

### K-19
**3 Katmanlı Log**
- **Bağlam:** Kurumsal bir platform için observability ve denetim gerekiyor. Farklı amaçlar için farklı log türleri.
- **Karar:** Üç ayrı log katmanı, her birinin kendi tablosu + endpoint'i:
  1. **Audit log** — admin aksiyonları (actor/action/entity/old-new JSONB/ip/trace_id). AOP `@AuditLog` annotation ile otomatik.
  2. **Giriş geçmişi (login history)** — user/success/ip/user_agent/reason. Login/refresh/register/logout'ta.
  3. **Request/trace log** — MDC traceId + `X-Request-Id` header. `RequestMetadataFilter` ile.
- **Durum:** UYGULANDI (core, 2026-07-27). 3 katmanın 2'si + trace altyapısı tamamlandı: (1) audit log — `AuditService` her admin aksiyonunu `t_audit_logs`'a yazar (User/Role/Group/PlatformCompany write metodlarında explicit `record(action, entityType, entityId, entityName)`); (2) login history — `LoginHistoryService` her login denemesini (success + failure, `reason` = `ErrorCode.code()`) `t_login_history`'e yazar (`AuthService.login` her outcome'unda, unknown email → `userId=null`); (3) request/trace — `RequestMetadataFilter` (`-102` order, tenant `-101` ve security `-100` öncesi) `X-Request-Id`/UUID + client IP (`X-Forwarded-For`/`X-Real-IP`/`getRemoteAddr`) + User-Agent'ı `RequestContext` ThreadLocal + MDC `traceId`'ye yazar (stabil per-request traceId; `ApiErrorFactory` MDC'den okur). Read side: `GET /api/v1/audit-logs` + `GET /api/v1/login-history` (`iam:audit:read`, sayfalı + opsiyonel filtre, `AuditQueryService`). Tüm yazılar `REQUIRES_NEW` + best-effort (audit asla iş sürecini bozmaz). **KALAN:** request/trace log **tablosu** (`GET /request-logs`) ve K-27 uzantıları (old/new-value, high-risk body capture mask-first, `@AuditLog` AOP aspect — AOP infra classpath'te, `@ApprovalRequired` approval workflow, anomaly detection).
- **Etki:** Yeni tenant migration `tenant/V3__audit_login_history.sql` (`t_audit_logs` + `t_login_history`); backend `web/` package (`RequestMetadataFilter` + `RequestContext`/`RequestMeta`); yeni `iam:audit:read` permission (`PermissionCatalog` → Admin rolüne seed). Mevcut tenant'lara `TenantMigrationRunner` V3'ü startup'ta uygular.

### K-20
**Admin/User/Log UI Faz 3 öncesi**
- **Bağlam:** K-18 sonrası backend Faz 2 tamamlanınca frontend geliyor. Built-in modül UI'ları (Tasks/Notes) beklenebilir ama admin/user yönetimi kritik.
- **Karar:** Epic 4.0.B (Admin/User/Log Management UI) Faz 3 öncesi gelir. Faz 4 core stack (bağımlılıklar, Tailwind, auth UI, router) burada kurulur. Tenant-scoped: her şirket kendi verisini görür.
- **Durum:** Planlandı (Faz 4.0.B, backend Faz 2 sonrası).
- **Etki:** Built-in modül UI'ları hâlâ Epic 4.1'de.

### K-21
**Hibrit Tenant Signup Verification (2026-07-20) — UYGULANDI**
- **Bağlam:** Mevcut `provisionTenant` open endpoint + ağır DDL (schema CREATE + Flyway) + subdomain/emailDomain squatting'e karşı korumasız. RBAC/auth kurulmadan önce signup yolunu sağlamlaştırmak gerekiyor.
- **Karar:** İki fazlı hibrit akış:
  1. `POST /api/v1/auth/company/register` — `PROVISIONING` Company + `TenantVerificationToken` yaratır (şema/migration YOK, hafif). `VerificationSender` ile doğrulama linki gönderir.
  2. `POST /api/v1/auth/company/verify` — token consumes -> SENKRON schema CREATE + Flyway tenant migration + admin user -> Company `ACTIVE`, token `usedAt`.
  Tetikleyici polling/event DEĞİL, kullanıcının linke tıklaması (HTTP request).
- **Durum:** UYGULANDI (Epic 2.0.C). İki fazlı senkron akış `TenantProvisioningService.createPendingCompany` + `verifyAndProvision` olarak bölündü. Admin email/password phase 1'de hash'lenip token'a gömülür, phase 2 kullanıcıya tekrar sorulmaz. Ek olarak `POST /api/v1/auth/company/suggest-subdomain` (slug önerisi, Türkçe karakter normalize).
- **Etki:**
  - `TenantVerificationToken` entity (`public` şema, `GeneratedIdAuditEntity` — soft-delete'siz, `usedAt` ile invalidasyon). Token admin credential'larını taşır (`adminEmail`, `adminPasswordHash` pre-hashed, `adminFirstName`, `adminLastName`).
  - `public/V3__organization_domains_and_verification_tokens.sql` migration: `t_tenant_verification_tokens` + `t_organization_domains` (K-32) + `email_domain` kolonu DROP.
  - `VerificationSender` interface + profile bazlı impl'ler: `test`->`InMemoryVerificationSender`, `dev`/`prod`->`LogVerificationSender` (mail starter Faz 5'te; prod gerçek mail gönderimi erteli — şu an prod link log'dan alınır).
  - `CompanyStatus.PROVISIONING` gerçekten kullanılır hale geldi.
  - Tenant signup admin email doğrulaması (`public` şema) ile tenant içi user email doğrulaması (`User.emailVerificationToken`, tenant şeması) AYNI şey DEĞİL — tenant içi akış Epic 2.9 kapsamında (entity field'ları hazır, flow bekliyor).
  - **SystemAdminBootstrap (K-24):** `provisionSystemTenant(request)` iki fazı arka arkaya çalıştırır, verify maili göndermez (bootstrap'te mail loop olmaz). Auto-verify.
  - **DEBT-10 kısmen çözüldü:** `createPendingCompany` tam transactional (yalnız DB write). `verifyAndProvision` `@Transactional` işaretli ama `CREATE SCHEMA` PostgreSQL'de implicit commit → DDL transaction dışına kaçar. Kısmi-write recovery idempotency ile (`CREATE SCHEMA IF NOT EXISTS`, token `usedAt` guard). Tam transactional DDL mümkün değil (PostgreSQL sınırlaması).
- **Not 1 (migration çakışması):** `public/V3` (tenant verification + org domains) `public/V2` (partial index) sonrası gelir. `tenant/V3` hâlâ boşta (audit/log migration'ı K-19 ile).
- **Not 2:** Backend/persistence AGENTS.md'leri ve ARCHITECTURE.md bu kararı "uygulandı" olarak yansıtıldı.

### K-32
**Organizasyon/Domain Refactor (1:N domains, emailDomain kaldır) — UYGULANDI**
- **Bağlam:** `t_companies.email_domain` tek string + UNIQUE + zorunlu idi. İki sorun: (1) bir organizasyonun BİRDEN FAZLA domain'i olabilir (holding şirketleri: `anakurumsal.com` + `yankurum.com`); (2) klüp/kişisel senaryosu (öğrenci gmail ile kayıt) domain gerektirmez. Ayrıca ileride LDAP/SSO bağlamak için org'nin birden fazla doğrulanmış domain'i olmalı.
- **Karar:** K-21 ile birlikte uygulandı:
  1. `t_companies.email_domain` kolonu + partial index'i DROP edildi (`public/V3`).
  2. `Company` entity'sinden `emailDomain` field kaldırıldı.
  3. `t_organization_domains` (1:N, `public` şema) — bir org N domain sahibi olabilir, opsiyonel (klüp/kişisel için boş kalabilir). `verified` boolean (custom domain doğrulama akışı ileride — Faz 5/enterprise; gelene kadar tüm domain'ler `verified=false` → self-register kapalı, invite-only).
  4. `findByEmailDomain` repository metodu kaldırıldı.
  5. `CompanyRegisterRequest`/`CompanyResponse`/`SystemAdminBootstrapProperties`'tan `emailDomain` kaldırıldı.
- **Durum:** UYGULANDI. Tablo adı `t_companies` KALDI (migration maliyeti yüksek; semantik "organization" ama entity/tablo adı geriye dönük uyum için korundu).
- **Etki:**
  - **Domain-bazlı self-register:** `t_organization_domains` `verified=true` row'larındaki domain'ler self-register'a açık (Epic 2.9 tenant içi user register). Boş tablo → invite-only.
  - **Custom domain doğrulama (DNS TXT/MX):** Faz 5/enterprise. K-21 ile tablo + `verified` kolonu hazır, akış ileride.
  - **LDAP/SSO:** enterprise fazında, doğrulanmış domain'lere LDAP bağlanır. K-32 bunun ön koşuludur.
  - **RISK-17 (partial unique index):** `t_organization_domains.domain` partial unique index (`WHERE is_deleted = FALSE`) — silinen domain tekrar kullanılabilir.

### K-22
**Tenant Domain Handoff / Schema Archival (2026-07-22) — PLANLANDI**
- **Bağlam:** Nadir ama gerçek bir senaryo: bir şirket aboneliğini kapatır/ödemez → `SUSPENDED`/`TERMINATED`. Daha sonra aynı email domain'i (örn. şirket iflası, domain başkasına geçti) farklı bir kişi tarafından tekrar ForgeSys'a kayıt olmak isteyebilir. Eski şirketin subdomain/emailDomain/schema_name değerleri serbest kalmalı, ama eski veri kaybolmamalı (arşiv).
- **Karar:** İki katmanlı yaklaşım:
  1. **Kısıt katmanı (RISK-17 partial index ile sağlandı):** Soft-delete edilmiş şirketin `subdomain`/`email_domain`/`schema_name` değerleri `WHERE is_deleted = false` partial index sayesinde aktif satırlar arasında benzersiz kalmaya devam ederken, silinen satır tekrar kullanılabilir. Yeni kayıt temiz geçer.
  2. **Fiziksel arşiv (operasyonel, platform admin):** Eski şirketin fiziksel şeması `ALTER SCHEMA tenant_X RENAME TO tenant_X_archived` ile yeniden adlandırılır. Yeni kayıt fresh `tenant_X` şeması + Flyway ile yaratılır; eski veri `tenant_X_archived`'da platformdan ayrı (orphan) kalır.
- **Durum:** PLANLANDI. Katman 1 (partial index) uygulandı (RISK-17, Epic 2.0.B). Katman 2 (fiziksel schema rename + reaktivasyon onay maili) platform admin tooling / Faz 6 (Billing & Abonelik) kapsamında gelecek.
- **Etki:** RISK-17 kapsamı açıkça `t_companies` dahil 9 index olacak şekilde tasarlandı (sadece tenant-side değil) — bu senaryoyu destekler. `CompanyStatus` yaşam döngüsü (ACTIVE → SUSPENDED → TERMINATED + reaktivasyon) Faz 6 ile netleşecek.

### K-23
**Global Password Pepper (HMAC pre-hash + BCrypt)**
- **Bağlam:** Şifre hash'leri DB'de BCrypt(12) olarak saklanıyor (RISK-13). BCrypt salt'ı hash'e gömüyor (per-user, standart) ama DB leak senaryosunda saldırgan hash tablosunu alıp offline brute-force yapabilir — pepper (DB dışında tutulan global secret) olmadan tek başına BCrypt yetersiz. Per-tenant pepper değerlendirildi: DB leak tehdit modeli için **ek güvenlik sağlamaz** (saldırgan DB'yi okuyor, pepper DB'de değilse zaten göremiyor — per-tenant'ın ek katkısı ancak "bir tenant'ın pepper'ı bağımsız sızarsa" senaryosunda, ki bu başka tehdit). Per-tenant pepper N key yönetimi + pepper kaybında o tenant'ın tüm şifreleri kurtarılamaz riskini getirir.
- **Karar:** **Global** pepper, BCrypt'in native pepper desteği olmadığı için **HMAC-SHA256 pre-hash** stratejisiyle (OWASP önerisi): raw şifre önce `HMAC-SHA256(pepper, password)` → Base64 (32 byte → 44 char, BCrypt 72-byte limit altında), sonra BCrypt(12). `PepperingPasswordEncoder` BCrypt'i wrap'lar. Pepper'lı hash'ler `{sf-peppered}` marker prefix'i ile işaretlenir; legacy pepper'sız BCrypt hash'ler (`$2a$12$...`) hâlâ `matches()` ile geçerli ve ilk başarılı login'de pepper'lı formata **lazy rehash** edilir (RISK-13 felsefesiyle aynı). Pepper `forgesys.security.password-pepper` / `PASSWORD_PEPPER` env var'ından; **boşsa startup fail-fast** (SecurityConfig). test/dev profilleri non-secret default sağlar; prod mutlaka gerçek secret sağlamalı.
- **Durum:** Uygulandı.
- **Etki:**
  - **DB leak tek başına artık hash kırımı için yetersiz** — pepper DB dışında (env/secret manager/config overlay).
  - **Pepper rotasyonu ŞU AN DESTEKLENMİYOR:** pepper değişirse tüm pepper'lı hash'ler geçersiz olur (legacy hash'ler hâlâ doğrulanır ama pepper'lı olanlar değil). Rotasyon gerektiğinde özel migration akışı tasarlanmalı (tüm kullanıcılar şifre sıfırlama).
  - Pepper'ı asla logla/commit etme (AGENTS.md "Never log sensitive data" kuralı).
  - **Per-tenant pepper** yalnızca BYOK/regülasyon (KVKK/HIPAA) gerektiğinde tekrar değerlendirilmeli — ve o zaman JWT signing key'leri de per-tenant yapılmalı (tutarlı crypto isolation).
  - `AuthService.login` artık read-write transaction (rehash write edebilir); pepper'lı user'larda no-op.
  - Mevcut `DelegatingPasswordEncoder`'a geçiş ertelendi (over-engineering şu an); algoritma değişimi (Argon2id) istenirse marker+wrapper yapısı taşınabilir.

### K-24
**System Tenant Bootstrap (rezerve privileged tenant)**
- **Bağlam:** Platformun kararlı bir privileged identity'e ihtiyacı var — tenant'ları yöneten `/api/v1/platform/companies` endpoint'leri (K-25) için `platform:company:*` yetkilerini taşıyan bir admin. Bunu manuel signup'a bırakmak operational yük (her deploy'ta/refresh'te elle tenant açmak) ve güvenlik açığı (public signup ile super-admin yaratma).
- **Karar:** `SystemAdminBootstrapRunner` (`ApplicationRunner`, `@Profile("!test")`): startup'ta rezerve `system` tenant'ını + admin user'ını idempotent olarak provision eder. Konfig `forgesys.bootstrap.system-admin.*` (application-dev.yaml default'ları gömülü). `TenantProvisioningService.provisionTenant`'ı yeniden kullanır → aynı DEBT-10 (non-transactional) borcuna tabi. Zaten `system` subdomain'inde bir Company varsa no-op. Hata loglanıp yutulur (bootstrap hatası startup'ı durdurmaz). RBAC seed (`RbacSeeder` → Admin role + tüm permission catalog) ve admin rol ataması ayrı startup adımında (`RbacSeeder.run`) yapılır.
- **Durum:** Uygulandı.
- **Etki:**
  - `system` subdomain'li tenant rezerve edilir — normal signup bu subdomain'i alamaz (validateUnique reddeder).
  - K-21 (iki fazlı signup) uygulandığında `SystemAdminBootstrapRunner` da **auto-verify** akışına geçer (`createPendingCompany` + token + aynı runner içinde `verifyAndProvision` — bootstrap'te mail tıklamayı önlemek için).
  - Konfig secret değildir (default dev password placeholder); prod `application-prod.yaml` / `.env` üzerinden gerçek credential sağlamalı. Default password ile prod'a çıkılmamalı.
  - DEBT-10 kapsamına girer (provisionTenant transaction'suz) — K-21 ile birlikte çözülür.

### K-25
**Platform Admin Namespace (cross-tenant super-admin)**
- **Bağlam:** Tenant-scoped `iam:*` permission'ları (User/Role/Permission/Group CRUD) tek bir tenant'ın içine hapsolur. Ama platformun kendisini yöneten işlemler var: tüm tenant'ları listelemek, bir tenant'ı SUSPEND/TERMINATE etmek, geleceğin billing/plan yönetimi. Bunlar **cross-tenant** işlemler — herhangi bir tenant'ın Admin rolüne verilemez.
- **Karar:** İkinci permission namespace: `platform:company:read` / `platform:company:write` (`PermissionCatalog`'te tanımlı). Yalnızca **system tenant**'ın Admin rolüne seed edilir (çünkü `RbacSeeder` her tenant'a Admin'e tüm permission'ları verir — system tenant'ın Admin'i bu platform yetkilerini taşır, normal tenant Admin'i de teknik olarak taşır ama cross-tenant veri erişimi `TenantFilter` + `public` şema izolasyonu ile kısıtlanır; normal tenant'ların platform endpoint'leri `@PreAuthorize`'den geçse bile `executeWithoutTenantContext` ile public şemada çalışır ve system admin'i tüm şirketleri görür). `PlatformCompanyController` (`/api/v1/platform/companies`) `@PreAuthorize("hasAuthority('platform:company:*')")` ile korunur.
- **Durum:** Uygulandı.
- **Etki:**
  - `PlatformCompanyService.findAll/findById/updateStatus` — `TenantContext`'i geçici olarak temizleyip (`executeWithoutTenantContext`) public şemada çalışır; tüm `t_companies` satırlarına erişir.
  - `CompanyStatus` yaşam döngüsü (ACTIVE → SUSPENDED → TERMINATED) Faz 6 (Billing) ile netleşecek; şimdilik `PATCH /platform/companies/{id}/status` manuel admin aracı.
  - **Bilinen zayıflık:** tüm tenant'lara seed edilen `iam:*` Admin rolü aynı zamanda `platform:*` de içerir → teorik olarak herhangi bir tenant Admin'i platform endpoint'lerini çağırabilir. K-21 sonrası veya yeni bir kararla `platform:*` permission'ları **sadece system tenant**'a seed edilecek şekilde `RbacSeeder` daraltılmalı ([RISK-18](#risk-18)).

### K-26
**RBAC Enforcement: Method Security (`@PreAuthorize`)**
- **Bağlam:** Permission namespace'leri (`iam:*`, `platform:*`) tanımlı, seed'leniyor, JWT claim'lere gömülüyor — ama controller'larda **enforce** edilmiyor. Yetkisiz erişim tek başına SecurityConfig'in "authenticated" kontrolüne dayanır; her authenticated user her endpoint'e ulaşabilir.
- **Karar:** `@EnableMethodSecurity` (`SecurityConfig`) + her korumalı controller metodunda `@PreAuthorize("hasAuthority('{namespace}:{resource}:{action}')")`. Namespace'ler `PermissionCatalog`'ten gelir; `CustomUserDetails` authorities = direct roles + active group roles → permissions. Self-service endpoint'ler (`/api/v1/users/me/**`) `@PreAuthorize`'süz, yalnızca "authenticated" — her user kendi profilini/şifresini yönetir.
- **Durum:** Uygulandı (Epic 2.9).
- **Etki:**
  - Tüm `iam:*` ve `platform:*` endpoint'leri `@PreAuthorize` ile korunur.
  - Yetkisiz istek → 403 `AUTH_ACCESS_DENIED` (`RestAccessDeniedHandler`, uniform shape).
  - Permission cache henüz YOK — her request `CustomUserDetailsService` JWT claim'lerden authorities'ı reconstruct eder (DB'siz). Redis cache (Epic 2.6) gelince `PermissionCacheService` devreye girer.

### K-27
**Audit & Log Genişletmesi (K-19'a ekler) — PLANLANDI**
- **Bağlam:** K-19 üç katmanlı log öngörüyor (audit + login history + request/trace), ama gerçek operasyonel/güvenlik senaryoları daha geniş kapsam gerektiriyor: başarısız login denemeleri, high-risk endpoint'lerde body loglama, anomali tespiti ve yüksek riskli işlem onayı (approval workflow).
- **Karar:** K-19 aşağıdaki eklemelerle genişletilir (Epic 2.10 uygulamasında netleşir):
  1. **Login history → başarısız denemeler de yazılır.** Sadece login/refresh/register/logout değil; bilinmeyen email, yanlış şifre, locked account, expired token her deneme `t_login_history`'e yazılır (success=false + reason). Brute-force tespiti bu veriden beslenir.
  2. **Request/trace log → high-risk endpoint'lerde body loglanır.** Default: sadece metadata (method/path/status/duration/userId/ip/traceId). **Ek olarak** create/delete/admin (`POST`/`DELETE`/`PATCH` `iam:*`/`platform:*`) endpoint'lerinde request body loglanır — ama maskeli (şifre/token/secret `[REDACTED]`). Body JSONB'a düşer, anomali/forensics için. Config-driven (hangi endpoint'ler "high-risk" `application.yaml`'da listelenir).
  3. **Anomaly detection (passif, alert bazlı).** Rate limit (X delete/dk/user), unusual pattern (normalin dışında bulk delete, gece işlemi, yeni lokasyon, yeni cihaz). Bunlar **block değil alert** — anomali tespit edilince K-29 notification tetiklenir. Active block Yok (UX'i bozmamak için); sadece gözlem + bildirim.
  4. **Approval workflow (aktif, çift onay).** Yüksek riskli işlemler (user delete, role delete, bulk operations) **ikinci bir admin onayı** gerektirir. İşlem önce `pending` state'inde yaratılır (audit log + ayrı `t_pending_actions` tablosu), ilk admin tetikler, ikinci admin onaylar/reddeder. Tenant-scoped. Hangi işlemler "approval-gerekli" config-driven (`iam:user:delete`, `iam:role:delete` default).
- **Durum:** PLANLANDI. K-19 (Epic 2.10) uygulamasında detaylandırılır.
- **Etki:**
  - `t_login_history` şeması `success` zaten var; `reason` enum genişletilir (bad_credentials/unknown_user/locked/expired/...).
  - `t_audit_logs`'a `request_body JSONB` kolonu (nullable) eklenir — sadece high-risk.
  - Yeni `t_pending_actions` tablosu (id/action_type/actor_id/payload JSONB/status/created_at/approved_by/approved_at).
  - Approval workflow service-layer'ı tetiklenmeli (`@ApprovalRequired` annotation veya açık servis çağrısı).
  - Storage artışı: high-risk body loglama + pending_actions → periyodik arşiv/temizlik job'u (Faz 5).

### K-28
**Session Management & Remote Revoke — PLANLANDI**
- **Bağlam:** Kullanıcıların aktif oturumlarını (hangi cihaz/IP/ne zaman login) görmesi, admin/yetkilinin şüpheli veya unutulmuş bir oturumu uzaktan kapatması gerekiyor. Mevcut mimaride JWT stateless (token client'ta), DB'de "aktif oturum" kaydı yok → aktif session listesi ve remote revoke mimariyle çelişiyor.
- **Karar:** İki katmanlı tasarım:
  1. **Active session management (runtime, stateful)** — Redis (Epic 2.6 bağımlılığı). Her refresh token = bir session kaydı (key: `session:{userId}:{sessionId}`, value: `{refreshTokenHash, device, ip, user_agent, loginAt, lastSeenAt}`, TTL = refresh token ömrü). `/api/v1/sessions` listesi bu cache'den okur. Revoke: Redis'ten key silinir + refresh token blacklist'e alınır (`TokenBlacklistService`). Stateless JWT access token revoke için `tokenInvalidBefore` (kullanıcı-bazlı "sonra geçerli token'lar invalid") veya granular access-token blacklist (Faz 2.5/2.6).
  2. **Session audit log (kalıcı, geçmiş analiz)** — yeni `t_sessions_log` tablosu (tenant şemasında). Event bazlı: `LOGIN` / `LOGOUT` / `SESSION_REVOKED` / `EXPIRED`. Kalıcı, geçmiş analizi/forensics. Active session listesi buradan değil Redis'ten.
  - **Admin/yetkili remote revoke:** `/api/v1/users/{id}/sessions` (list) + `DELETE /api/v1/users/{id}/sessions/{sessionId}` (revoke). İzin: `iam:user:write`. Self-service: `/api/v1/users/me/sessions` (kullanıcı kendi oturumlarını görür/kapatır).
- **Durum:** PLANLANDI. Epic 2.5 (refresh token) + Epic 2.6 (Redis) bağımlılığı → sonrasında uygulanır.
- **Etki:**
  - Refresh token rotation (Epic 2.5) ile entegre — her rotate yeni sessionId üretir.
  - Redis key TTL → otomatik expire; ama `t_sessions_log`'a `EXPIRED` event yazımı scheduled job veya lazy (sonraki erişimde).
  - `UserAccount.tokenInvalidBefore` zaten var — full revoke (tüm session'lar) için kullanılabilir; granular (tek session) için Redis blacklist.
  - "Device" bilgisi `User-Agent` header'ından parse edilir (basit veya bir library ile — Faz 2.10 detaylandırır).

### K-29
**Security Notification Subsystem — PLANLANDI**
- **Bağlam:** Güvenlik olayları (şüpheli login, yeni cihaz, bulk delete, başarısız login spike'ı, parola değişimi, rol değişikliği) ilgili tarafa bildirilmeli: etkilenen kullanıcıya, organizasyon adminine veya (gelecekte) platform adminine. Şu an mail gönderme altyapısı bile YOK (K-21 prod VerificationSender erteli). Bildirim kanalı + şablon + abonelik (notification preference) gerekir.
- **Karar:** `NotificationService` (tenant-scoped) + iki kanal:
  1. **In-app** — `t_notifications` tablosu (tenant şeması): user_id/type/Severity/payload JSONB/read_at/created_at. `/api/v1/notifications` (list, mark-read). Real-time için WebSocket/SSE (Faz 5+). Şimdilik polling.
  2. **Mail** — `MailNotificationSender` (prod), `LogNotificationSender` (dev), `InMemoryNotificationSender` (test). K-21 `VerificationSender` ile aynı mail altyapısını paylaşır (`spring-boot-starter-mail` Faz 5'te).
  - **Notification type catalog** — enum: `SUSPICIOUS_LOGIN`, `NEW_DEVICE_LOGIN`, `LOGIN_FROM_NEW_IP`, `FAILED_LOGIN_SPIKE`, `PASSWORD_CHANGED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `BULK_DELETE_ALERT`, `SESSION_REVOKED_BY_ADMIN`, `APPROVAL_REQUESTED`, `APPROVAL_DECISION`. Her tipin template'i (subject + body HTML, `infra/templates/`).
  - **Subscription/preference** — `t_notification_preferences` (user_id/type/in_app/mail enabled). Default: kritik (şifre değişimi, şüpheli login) her iki kanal; diğerleri in-app only.
- **Durum:** PLANLANDI. Epic 2.10 (Audit) ve K-27 (anomaly detection) ile entegre. Mail bağımlılığı Faz 5.
- **Etki:**
  - `NotificationService.send(userId, type, payload)` — audit events (K-19), anomaly detection (K-27), session revoke (K-28) tarafından çağrılır.
  - Kullanıcının "bir organizasyonda birden fazla rolü/maili" senaryosu (ileride) için notification routing esnek olmalı (şimdilik tek user = tek mail).
  - Multi-language (TR/EN) template'ler `infra/templates/` (i18n).

### K-30
**Activity Feed (Jira-style user-facing event stream) — PLANLANDI**
- **Bağlam:** Audit log (`t_audit_logs`) raw seviyede (actor/action/entity/old-new JSONB) — admin forensics için ideal ama normal kullanıcı için okunamaz. "Ali 'Tasarım Ekibi' grubunu oluşturdu", "Ayşe Mehmet'i 'Editor' rolüyle davet etti" gibi insan-okur activity feed kullanıcı/org admini için değerli (ekip ne yapıyor görünürlüğü, onboarding, audit-lite).
- **Karar:** Audit log üstüne **materialized activity view** — iki seçenek değerlendirilecek (K-19 uygulamasında netleşir):
  - **(a) Materialized view / sorgu türetme:** `t_audit_logs` üstünde bir SQL/view veya servis katmanı `actor + action + entity_payload → human-readable text` üretir. Ek tablo YOK. Sorgu maliyeti ama tutarlı.
  - **(b) Ayrı `t_activities` tablosu:** her audit event yazıldığında async bir job listener activity satırı yazar (user-friendly text + category + visibility_scope). Daha hızlı okuma ama çift-yazma + tutarlılık riski.
  - Önerilen: (a) önce (basitlik), (b) performans sorun olursa geçiş.
  - **Visibility scope:** her activity public (tüm org) / team-only / private. Kullanıcı kendi activity'sini ve (yetkisi dahilinde) takım/org activity'sini görür. `/api/v1/activities` (sayfalı, filtreli).
  - **Activity text generation:** enum-driven template map — `{action}_{entity}` → template (`{actorFullName} '{entityName}' {actionPastTense}...`). i18n (TR/EN). Örnekler: `create_group` → "{actor} '{groupName}' grubunu oluşturdu", `assign_role` → "{actor} {targetUser} kullanıcısına '{roleName}' rolünü verdi".
- **Durum:** PLANLANDI. Backend (Epic 2.10 ile), UI (Faz 4 — activity feed ekranı).
- **Etki:**
  - Audit log (K-19) uygulamasında activity-friendly entity naming (her event `entity_name` human-readable tutar).
  - Faz 4 UI'da activity feed ekranı (K-20 admin panel'e eklenir).
  - Tenant-scoped — cross-tenant activity yok (RISK-18 çözülene kadar platform admin activity feed'i ayrı, yalnızca system tenant için).

### K-35
**`all_permissions` flag — Admin implicit süper-kullanıcı + "ALL" rol kısayolu**
- **Bağlam:** İki ayrı şikayet birleşti: (1) runtime'da `POST /permissions` ile eklenen permission Admin rolüne **hiçbir zaman** (restart'ta bile) ulaşmıyordu — `RbacSeeder.ensureAdminRole` Admin'i yalnızca hardcoded `PermissionCatalog.ALL` listesinden besliyordu, `PermissionService.create` hiçbir role atama yapmıyordu; yetkiler JWT'ye issue anında gömülü olduğundan admin yeni permission'ı göremiyordu ("haberleri olmuyor"). (2) Rol oluştururken tek tek tüm permission'ları seçmek yorucu. Önceki tasarım grant-based (yeni permission'ı Admin'e ata) düşünüldü ama kullanıcı kararı: *"admin role atama yapılmamış olsa bile tüm yetkiye sahip olsun"* — yani explicit grant defter tutmayı ve permission silme UX'ini (önce Admin'den ayır) bozmadan implicit süper-kullanıcı semantiği istendi.
- **Karar:** `t_roles.all_permissions BOOLEAN NOT NULL DEFAULT FALSE` (tenant `V8`, `TenantMigrationRunner` mevcut tenant'lara uygular). Bir rol bu flag'i taşıyorsa, `CustomUserDetailsService.resolvePermissionNames` parent-closure walk'ından **sonra** o tenant'taki **tüm permission isimlerini** döndürür (`PermissionRepository.findAllNames` JPQL projection) — `t_role_permissions`'a hiçbir satır yazılmadan. Closure'dan sonra kontrol → parent'ı all-permissions olan rol de all-permissions sayılır. **İki kullanım, tek mekanizma:** (1) `RbacSeeder` Admin rolüne `all_permissions=true` set eder + explicit permission satırlarını clear eder (delete-UX temiz: katalog permission'ı silmek Admin yüzünden `in_use` bloğa takılmaz); (2) `PUT /roles/{id}/permissions` artık `{all:true}` (flag set + explicit set clear) veya `{permissionIds:[...]}` (explicit mod, flag false) kabul eder — "ALL" kısayolu. `RoleResponse.allPermissions` state'i expose eder. **İmmediacy:** `PermissionService.create` (ve rename'de `update`) `SessionRevocationService.revokeAllPermissionsRoleHolders` çağırır → tüm all-permissions kullanıcıların token'ı düşer, silent refresh ile yeni permission JWT'ye yansır (runtime permission'lar `@PreAuthorize`'da statik olmadığından aslında enforcement kırılmaz; bu tamamen "haberim oldu" immediacy'si + dinamik katmanlar içindir).
- **Tradeoff / sınır:** Yetkiler issue anında snapshot olduğundan, **yeni katalog permission'ı** (release'da gelen, `@PreAuthorize`'da statik) eski token'da yok → admin re-login bekler. Runtime permission'lar için revoke bunu kapatır; release/catalog için opsiyonel startup-revoke mitigation var (şimdi değil). `@PreAuthorize("hasAuthority('x')")` enforcement katmanı **dokunulmadı** (güvenlik-critical core'a cerrahi yok) — admin'in JWT'si kelimenin tam anlamıyla tüm permission isimlerini içerir, mevcut `hasAuthority` olduğu gibi çalışır. Frontend değişmedi (`/me` authorities tüm isimleri listeler).
- **Durum:** UYGULANDI (2026-07-31). 302 test yeşil (H2). Önceki planlanmış name-based Admin detection'ı supersede eder (flag replaces name). RISK-18 ile ilişkili: `all_permissions` Admin'e tüm IAM + platform yetkilerini implicit verir (pratikte TenantFilter public şema erişimini yönetir).
- **Etki:** Admin (ve herhangi bir "ALL" rolü / onu taşıyan group üyesi) artık runtime permission'lardan haberdar; rol permission atama UI'ında "ALL" toggle; permission silme artık Admin yüzünden bloklanmaz.

### K-36
**Pre-1.0.0 migration squash — migration geçmişini `V1`'e indir (2026-08-22)**
- **Bağlam:** Proje henüz hiçbir prod ortama deploy edilmedi (tek geliştirici, local DB'ler). Buna rağmen migration geçmişi `public/V1..V3` + `tenant/V1..V8` olarak birikmişti; developer'lar "normal olmayan" migration akışından (squash edilemeyecek kadar büyümeden) şikayetçiydi. Versiyon 1.0.0'a ulaşılmadı — checksum/geçmiş uyumu gözetilmesi gereken deploy edilmiş hiçbir DB yok.
- **Karar:** Pre-1.0.0 penceresinden yararlanılarak her iki location'daki tüm migration'lar **`V1.x` baseline ailesine** indirildi (final durum birleştirildi; dotted versiyonlar Flyway'de sırayla koşar, her biri ayrı checksum satırı üretir — dosya bazlı kategorizasyon, tek dosya şişmesi yok):
  - `public/V1__tenant_registry.sql` (t_companies + t_organization_domains) + `public/V1.1__signup_verification_tokens.sql` (t_tenant_verification_tokens)
  - `tenant/V1__iam_users.sql` (t_users/accounts/profiles) + `V1.1__iam_rbac.sql` (roles/permissions/groups + join'ler + t_role_parents + all_permissions) + `V1.2__audit.sql` (t_audit_logs + t_login_history + append-only trigger'lar) + `V1.3__pm_projects_tasks.sql` (t_projects + t_tasks)

  Squash sırasında iki ertelenmiş Faz F kalemi bedava kapatıldı: (1) ölü `t_refresh_tokens` tablosu + `RefreshToken` entity'si + `RefreshTokenRepository` silindi (refresh zaten Redis-first, K-34); (2) `version BIGINT NOT NULL DEFAULT 0` tüm soft-delete tablolarına gömüldü. Partial unique index'ler doğrudan yazıldı (V2'nin "constraint yarat → DROP → partial index" dansı kalktı). `baselineOnMigrate` her yerden kaldırıldı (`TenantMigrationSupport`, dev/prod yaml, `CrossTenantIsolationTest`) — fresh-DB-only dünyada gereksiz; non-empty şemada baseline V1'i sessizce atlayıp şemayı boş bırakma riski taşırdı.
- **Durum:** UYGULANDI (2026-08-22). Local DB'ler sıfırlandı (`infra/data/postgres` silindi + recreate) — checksum değiştiğinden sıfırlamayan herkes Flyway validation hatası alır (README troubleshooting).
- **Etki:** Yeni migration'lar her iki location'da `V2`'den devam eder. `TenantMigrationRunner` (RISK-16) değişmedi — yeni tenant migration'ları mevcut tenant'lara yine startup'ta uygular. Tarihi kayıtlardaki `V2..V8` ref'leri tarihî gerçeklik olarak duruyor; güncel şema kaynağı `V1.x` baseline ailesi + üstüne gelen yeni versiyonlar.

---

## Risk Kayıtları (RISK-XX)

### RISK-3
**AuditorAware hardcoded "system"**
- **Bağlam:** JPA auditing için `AuditorAware` şu an her zaman `"system"` döndürüyor. Kimliği doğrulanmış kullanıcı yok.
- **Karar:** Auth kurulunca SecurityContext'ten gerçek userId alınacak. **Ancak** tenant signup endpoint'leri her zaman `"system"` ile audit edilir (tenant signup context'inde kimliği doğrulanmış kullanıcı yok) — bu beklenen durum.
- **Durum:** ÇÖZÜLDÜ (2026-07-24, [RISK-33](#risk-33) ile). `AuditorAware` artık SecurityContext'ten authenticated user'ın `userId`'sini okuyor; signup/provisioning/startup (kimliği doğrulanmış kullanıcı yok) hâlâ `"system"` fallback'ine düşüyor — bu beklenen durum.

### RISK-10
**@Async thread'lerde TenantContext taşınmaz**
- **Bağlam:** `TenantContext` ThreadLocal. Spring `@Async` yeni thread pool'da çalışır -> context kaybolur.
- **Karar:** `TaskDecorator` ile TenantContext + SecurityContext propagation. Audit/email async işler için gerekli.
- **Durum:** Planlandı (Faz 2.0.B).

### RISK-13
**BCrypt strength (ÇÖZÜLDÜ)**
- **Bağlam:** Mevcut `BCryptPasswordEncoder()` default strength 10. Güvenlik standardı 12.
- **Karar:** Faz 2.3'te `BCryptPasswordEncoder(12)`'ye geçilecek. Migration stratejisi (mevcut hash'ler) önce spike ile netleştirilecek.
- **Durum:** ÇÖZÜLDÜ (Epic 2.3). Spike sonucu: BCrypt self-describing (cost factor hash'e gömülü) olduğu için mevcut strength-10 hash'ler hâlâ `matches()` ile validate olur; yenileri 12'de encode edilir → lazy migration (sonraki şifre değişiminde).

### RISK-14
**oauth2-resource-server jwt filter aktif edilmez (UYGULANDI)**
- **Bağlam:** `spring-boot-starter-oauth2-resource-server` (Nimbus) RSA asimetrik imzalama (RS256) için seçildi (jjwt yerine). Ama auto-config filter `tokenInvalidBefore` check yapamaz.
- **Karar:** `.oauth2ResourceServer().jwt()` auto-config filter **AKTİF EDİLMEZ**. Custom `JwtAuthenticationFilter` yazılır (cookie->decode->SecurityContext).
- **Durum:** UYGULANDI (Epic 2.4). **NOT:** İlk-çalışan-login diliminde filter sadece imza+expiry doğrular; Redis blacklist + DB `tokenInvalidBefore` revoke kontrolü logout/refresh ile 2.5/2.6'da gelir.

### RISK-15
**DateTimeProvider bug (ÇÖZÜLDÜ)**
- **Bağlam:** `@CreatedDate` OffsetDateTime populate edilmiyordu — Hibernate varsayılan DateTimeProvider UTC timezone'u doğru set etmiyordu.
- **Karar:** Özel `DateTimeProvider` bean (UTC) tanımlandı, `MultiTenancyJpaConfig`'te.
- **Durum:** ÇÖZÜLDÜ.

### RISK-16
**Yeni tenant migration mevcut tenant'larda çalışmaz (ÇÖZÜLDÜ)**
- **Bağlam:** Programmatik tenant Flyway migration sadece yeni provision edilen tenant'larda çalışır. Mevcut tenant'lar V1'de takılır; yeni V2 tenant migration'ı onlara uygulanmaz.
- **Karar:** `TenantMigrationRunner` (`ApplicationRunner`, `@Profile("!test")`) — startup'ta tüm `t_companies` şemalarını `TenantMigrationSupport` üzerinden Flyway migrate eder. Yeni tenant migration'ından ÖNCE gelmeli.
- **Durum:** ÇÖZÜLDÜ (Epic 2.0.B). `TenantMigrationSupport` bean'i ortak Flyway mantığını taşır (hem runner hem `TenantProvisioningService` kullanır). Tek tenant hatası diğerlerini durdurmaz (per-tenant try/catch + log). Test profilinde kapalı (`@Profile("!test")` — H2 + flyway kapalı).

### RISK-17
**Soft-delete + UNIQUE çakışması (ÇÖZÜLDÜ)**
- **Bağlam:** DB seviyesi UNIQUE constraint soft-delete ile çakışır (silinmiş satır kalır, aynı isimde yenisi insert edilemez).
- **Karar:** Partial index: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Sadece `SoftDeleteAuditEntity` subclass'ları için. `GeneratedIdAuditEntity` subclass'ları (soft-delete'siz) normal UNIQUE kullanır.
- **Durum:** ÇÖZÜLDÜ (Epic 2.0.B). `public/V2` + `tenant/V2` ile 9 partial index: public `t_companies` (name/subdomain/email_domain/schema_name) + tenant `t_users`(username,email)/`t_roles`/`t_permissions`/`t_groups`(name). K-22 domain-handoff senaryosunu destekler. Join tabloları ve `t_refresh_tokens.token` normal UNIQUE kaldı. Gerçek DB doğrulaması Epic 3.X Testcontainers'da (H2 test profilinde Flyway kapalı).

### RISK-18
**`platform:*` permission'ları tüm tenant'lara seed ediliyor**
- **Bağlam:** K-25 platform admin namespace'ini getirdi ama `RbacSeeder.ensureAdminRole` her tenant'ta Admin rolüne `PermissionCatalog.ALL`'ı veriyor — `ALL` listesi `platform:company:read/write` içeriyor. Yani teorik olarak herhangi bir tenant'ın Admin rolü platform endpoint'lerine sahip. Pratikte `TenantFilter` + `executeWithoutTenantContext` cross-tenant erişimi system admin'e yönlendiriyor olsa da, bu defense-in-depth açığıdır.
- **Karar:** `RbacSeeder` `platform:*` permission'larını yalnızca **system tenant**'a (subdomain `system` veya ayrı bir konfig ile işaretlenen tenant) seed edecek şekilde daraltılacak. `PermissionCatalog.ALL` ikiye bölünecek: `IAM_PERMISSIONS` (her tenant) + `PLATFORM_PERMISSIONS` (sadece system tenant).
- **Durum:** Açık. K-21 sonrası veya ayrı bir hardening epic'inde çözülür.
- **Etki:** Çözülene kadar her tenant Admin'i teorik olarak platform yetkilerine sahip; praktikte TenantFilter public şema erişimini yönetir.

### RISK-19
**JWT tenant claim doğrulanmıyor — cross-tenant privilege escalation (P0)**
- **Bağlam:** `JwtAuthenticationFilter` JWT'den `tenant` claim + `authorities`'i doğrudan principal'a yazıyor ama `TenantFilter`'ın çözdüğü request tenant (subdomain) ile JWT `tenant` claim karşılaştırılmıyor. RBAC yetkileri (JWT) ile data scoping (`TenantContext`) ayrışık → Tenant-A admin token'ı Tenant-B'de geçerli. Koleksiyon endpoint'leri (`/users`, `/roles`) tamamen istismar edilebilir: saldırgan `a.forgesys.app`'ten aldığı admin token'ını `b.forgesys.app/api/v1/users`'a replay eder → Tenant-B user'larını okur/yazar/siler.
- **Karar:** `JwtAuthenticationFilter.doFilterInternal`'da decode sonrası `TenantContext.getCurrentTenant()` ile JWT `tenant` claim eşleşmezse `SecurityContextHolder.clearContext()` + chain devam (→ 401). Principal `tenantSchema`'sını claim'den değil `TenantContext`'ten al.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz A. `JwtAuthenticationFilter.authenticateIfTenantMatches` JWT `tenant` claim'ini `TenantContext` ile karşılaştırır (boş context → `"public"` normalizasyonu, `TenantIdentifierResolver` ile aynı); mismatch → `clearContext` (→ 401). Principal `tenantSchema`'sını claim'den değil context'ten alır. `BearerTokenAuthTest.crossTenantBearerTokenIsRejected` doğrular; platform cross-tenant yolu için `PlatformCompanyControllerTest` token-context eşleşecek şekilde güncellendi. Gerçek çapraz-tenant izolasyon testi RISK-20 (Testcontainers) ile. [AGENTS.md Refactor Roadmap](../AGENTS.md#refactor-roadmap-2026-07-24-review).

### RISK-20
**Cross-tenant isolation testi yok (P0)**
- **Bağlam:** Tüm test suite'i tek H2 `public` şemasında, `TenantContext` unset → resolver hep `"public"` döner → `SET search_path` mekanizması (izolasyonun omurgası) **hiç test edilmemiş**. RISK-19 dahil tüm tenant-scoped yolların izolasyonu doğrulanmamış. AGENTS.md "tenant verisi sızdırma en kritik bug sınıfı" diyor ama test yok.
- **Karar:** Testcontainers + PostgreSQL ile iki gerçek tenant şeması yaratan isolation test altyapısı (ROADMAP Faz 3.X'ten öne çekilir). Seed user tenant-A'da, `TenantContext` tenant-B'ye set, `GET /users/{id}` → 404 assert.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz B. `CrossTenantIsolationTest` (`postgres:16-alpine`, `@ServiceConnection`) iki tenant şeması provision edip (tenant_a `provisionSystemTenant` ile — RISK-26 da doğrulandı; tenant_b manuel CREATE SCHEMA+migrate) çapraz-tenant `SET search_path` izolasyonunu kanıtlar: tenant_a verisi tenant_b'de görünmez, tersi de. `-Dforgesys.pg.it=true` gate'i → varsayılan `mvn clean install` Docker'SIZ yeşil; IT gerçek PG 16.11'de geçti. Testcontainers deps (BOM-managed, `testcontainers-postgresql` 2.x + `spring-boot-testcontainers`) backend test scope'a eklendi.

### RISK-21
**`tokenInvalidBefore` kontrol edilmiyor (P1)**
- **Bağlam:** `UserAccount.tokenInvalidBefore` alanı var ama **hiçbir filter/service kontrol etmiyor** (grep doğruladı — sadece entity/Javadoc/docs). Logout sadece cookie expire, JWT geçerli kalır (15 dk). `changePassword`/`resetPassword` token invalidate etmiyor → çalınan token, şifre değiştirilse bile çalışmaya devam eder.
- **Karar:** `JwtAuthenticationFilter`'a `tokenInvalidBefore` kontrolü (issue sonrası Redis cache; ilk dilimde DB lookup) + `changePassword`/`resetPassword`/`logout`'ta `tokenInvalidBefore = now()` set. Epic 2.5/2.6 (Redis blacklist) ile tamamlanır.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz A. `UserRepository.findTokenInvalidBefore(userId)` tek-kolon JPQL projection (UserAccount `@MapsId` shared-PK — JOIN/lazy-proxy yok); `JwtAuthenticationFilter.isRevokedByTokenInvalidBefore` her authenticated request'te bunu çağırır, `iat < tokenInvalidBefore` (her ikisi saniyeye floor) → `clearContext` (→ 401). **Set noktaları:** `UserService.changePassword` + `resetPassword` + `revokeTokens(userId)` (yeni) → `account.setTokenInvalidBefore(OffsetDateTime.now())`; `AuthController.logout` principal'dan userId alıp `userService.revokeTokens` çağırır. **Tradeoff:** Redis cache (Epic 2.6) yok → her authenticated request ekstra 1 küçük indexed sorgu (tolere edilebilir; user_account küçük + index'li). Saniyeye floor: JWT `iat` NumericDate saniye çözünürlükte, naive nano compare → aynı saniyede mint+revoke token'ı reject ederdi; floor ile "aynı saniyede mint, revoke kararı belirsiz → accept" garantisi (hızlı re-login korunur). **Kapsam:** user-scoped revoke (kullanıcının tüm outstanding token'ı); granular per-session (tek token) revoke Epic 2.6 (Redis access-token blacklist). **RISK-22 kapatılması:** brute-force lockout (`AuthService.login`'de `lockedUntil` set) `tokenInvalidBefore` set ETMEZ — kilitlenen hesabın elindeki token'ı TTL süreince hâlâ geçerli; lockout anında da revoke etmek istenirse `AuthService.login`'e `invalidateTokens` çağrısı eklenebilir (kasıtlı olarak eklenmedi — her failed attempt'ta DB yazısı + RISK-22 scope'undan dışarı). Test: `BearerTokenAuthTest.tokenInvalidBeforeRevokesPreviouslyIssuedToken` / `tokenInvalidBeforeDoesNotAffectNewerToken` (filter-side); `UserProfileControllerTest.changePasswordRevokesPreviouslyIssuedAccessToken` (e2e changePassword → eski cookie reject); `changePasswordSucceedsAndInvalidatesOldPassword` (post-change fresh login → old password reject).
- **Etki:**
  - Çalınan token, kullanıcı şifre değiştirince / logout yapınca / admin reset edince anında geçersiz (TTL bekleme yok).
  - Çok-cihazlı logout yan etkisi: tek cihazdan logout → tüm cihazlardan logout (user-scoped). Granular çözüm Epic 2.6.
  - Filter artık DB-bağımlı — önceki DB'siz stateless principal reconstruction pattern'dan sapma; performans maliyeti Redis ile azaltılacak.
  - Frontend tarafında changePassword/resetPassword/logout sonrası otomatik re-login akışı (401 → login redirect) doğru çalışmalı.

### RISK-22
**Brute-force / lockout koruması yok (P1)**
- **Bağlam:** `failedLoginAttempts`/`lockedUntil` entity'de var ama **referans yok** (sadece comment'ler). Public `/auth/login`'de rate-limit/lockout yok. BCrypt(12) + pepper her tahmini yavaşlatsa da paralel credential stuffing'e karşı korumasız. RISK-21 ile birleşince → çalınan token 15 dk revoke edilemiyor.
- **Karar:** `AuthService.login`'de attempt counting + `lockedUntil` backoff + rate-limit (IP/tenant/email bazlı, Bucket4j veya Redis). Yeni `auth_account_locked` ErrorCode (kalan deneme sayısını sızdırmadan).
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz A. `AuthService.login` (artık `User` entity'yi tek sefer yükler, authority resolution + lazy pepper rehash aynı transaction'da) `failedLoginAttempts`/`lockedUntil` kullanır: 5 yanlış → 15dk lock, yeni `ErrorCode.AUTH_ACCOUNT_LOCKED` (423). Lock-expiry'de counter sıfırlanır (yeni deneme hakkı). `@Transactional(noRollbackFor=AuthException.class)` — attempt artışı `bad_credentials` throw'u ile rollback olmaz. **Kapsam:** login-scoped (filter DB lookup RISK-21 ile). IP/email rate-limit Redis (Epic 2.6) sonrası. Kilitlenen hesabın eldeki token'ı (≤ TTL) RISK-21 gelene kadar geçerli. `AuthControllerLoginTest` lockout + unlock test'leri doğrular.

### RISK-23
**Prod'da RSA key eksikse sessizce ephemeral (P1)**
- **Bağlam:** `RsaKeys.resolve` key yoksa `log.warn` + 2048-bit ephemeral üretiyor. Prod'da key unutulursa: (a) app yeşil başlar (warning log JSON/ECS log'larında gözden kaçar), (b) token restart'ta survive etmez, (c) **çok-instance dağıtımda her instance farklı key** → instance A'nın token'ı instance B'de fail (rastgele 401) veya "geçerli issuer" kümesi sessiz genişler.
- **Karar:** Prod profilinde key yoksa fail-fast (`IllegalStateException("jwt.rsa.* must be configured in prod")`).
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz A. `RsaKeys.resolve(properties, failIfUnconfigured)` — prod profilinde (`JwtConfig` `Environment.acceptsProfiles(Profiles.of("prod"))`) key yoksa `IllegalStateException`. Dev/test ephemeral korunur. `RsaKeysTest` doğrular.

### RISK-24
**Access token cookie `Secure` değil (P1)**
- **Bağlam:** `jwt.cookie-secure` default `false`, hiçbir YAML'da override yok → prod'da access token cookie `Secure` attribute'suz. HTTP düşürme/redirect/mixed content/internal network yollarında cookie cleartext gider. `SameSite=Lax` bunu engellemez.
- **Karar:** `application-prod.yaml`'a `jwt.cookie-secure: true`. (Ek: `@Value` yerine `@ConfigurationProperties` daha temiz.)
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz A. `application-prod.yaml`'a `jwt.cookie-secure: true` (dev/test default `false`). `@Value` → `@ConfigurationProperties` taşınması Faz F'de.

### RISK-25
**Token consumption race condition (P1)**
- **Bağlam:** `TenantProvisioningService.verifyAndProvision` token'ı read-modify-write; iki eşzamanlı istek aynı token'la → ikisi de `isUsed()` kontrolünden geçer. `TenantVerificationToken` `GeneratedIdAuditEntity`'den → `@Version`/optimistic lock yok, pessimistic lock yok. Biri admin user insert'te unique constraint patlar ama `CREATE SCHEMA` + Flyway zaten implicit commit oldu → PROVISIONING Company yarım kalır.
- **Karar:** Repository'ye `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<TenantVerificationToken> findByTokenForUpdate(String)` veya conditional UPDATE (`UPDATE ... SET used_at=now() WHERE token=? AND used_at IS NULL`, etkilenen satır 0 → `TENANT_TOKEN_ALREADY_USED`).
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz C. Conditional UPDATE seçildi (`PESSIMISTIC_WRITE` yerine — H2 + PG portable, lock-timeout tuning yok, single-column write read-modify-write window barındırmaz). `TenantVerificationTokenRepository.claimToken(token, now)` `@Modifying @Query("UPDATE ... SET usedAt = :now WHERE token = :token AND usedAt IS NULL")` → `int` row count. `verifyAndProvision` akışı: SELECT validate (exists/expired/company-status) → `claimToken` → 0 row → `TENANT_TOKEN_ALREADY_USED` (geçerli kodların — `INVALID`/`EXPIRED` — korunması için claim öncesi SELECT şart). In-memory entity sync için `verification.setUsedAt(claimedAt)`; eski `tokenRepository.save(verification)` kaldırıldı (claim zaten DB'ye yazdı). **Test:** `TenantProvisioningServiceTest.verifyAndProvision_concurrentClaimLost_throwsAlreadyUsed` (claimToken 0 döner → ALREADY_USED, hiç provisioning adımı çalışmaz); mevcut `validToken`/`usedToken`/`expiredToken`/`provisionSystemTenant` test'leri claimToken stub'ı ile uyumlandı. **Doğrulama kapsamı:** unit (Mockito) — gerçek concurrent race davranışı Testcontainers (RISK-20 altyapısı) ile ayrı doğrulanabilir; şimdilik atomic UPDATE'in DB-semantiği (single-row conditional write) invariant'ı yeterli.

### RISK-26
**Mid-transaction TenantContext switch (ÇÖZÜLDÜ)**
- **Bağlam:** `TenantProvisioningService.createAdminUser` `verifyAndProvision` transaction içinde tenant context switch ediyor (`setCurrentTenant(schemaName)`). Transaction başında `public` şeması connection'ı edinir (DELAYED_ACQUISITION_AND_HOLD), sonra User insert tenant şemasına beklenir ama Hibernate connection'ı tutuyorsa yanlış `search_path`'e yazma riski → `public.t_users` yok → "relation does not exist". **H2 tek-şema olduğu için test yakalamıyor** (RISK-20 ile ilişkili).
- **Karar:** İki adımlı düzeltme uygulandı:
  1. `createAdminUser` `@Transactional(propagation = Propagation.REQUIRES_NEW)` + self-proxy (`ObjectProvider<TenantProvisioningService> self`) ile ayrı transaction'da çalışır. RbacSeeder pattern'i (ObjectProvider self).
  2. **Kritik:** `setCurrentTenant` `verifyAndProvision`'da `self.getObject().createAdminUser(...)` çağrısından **ÖNCE** yapılır. `CurrentTenantIdentifierResolver` Hibernate session açıldığında (transaction başında) tenant identifier'ı çözer — session açıldığında context set değilse "public" cache'lenir, gövdedeki `setCurrentTown` çok geç kalır.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Gerçek PostgreSQL + Docker ile doğrulandı: `SystemAdminBootstrapRunner` başarılı, system tenant ACTIVE, 12 tablo migrate edildi, admin user + RBAC seed tamam. 112 test yeşil.
- **Etki:** `createAdminUser` artık her zaman doğru tenant schema'sında çalışır. RISK-20 (gerçek PG test altyapısı) çözülünce daha geniş çaplı doğrulama yapılacak; ama bu spesifik bug kapandı.

### RISK-27
**N+1 — `findAll` userProfile/userAccount (P1)**
- **Bağlam:** `UserRepository.findAll` `@EntityGraph({"roles","groups"})` — `userProfile`/`userAccount` eksik. `UserService.toResponse` ikisine de erişir (`profile.getFirstName()`, `user.getUserAccount().isEnabled()`). Inverse `@OneToOne` LAZY bilinen sorunlu alan + sayfa başına N user → 2N ekstra SELECT. Multi-tenant'ta her sorgu tenant schema geçişi → maliyet katlanır.
- **Karar:** EntityGraph'a `userProfile`, `userAccount` ekle.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz D. `UserRepository.findAll(Pageable)` `@EntityGraph`'ı `{"roles","groups","userProfile","userAccount"}` oldu (roles/groups `Set` → multiple-bag yok). N+1 (sayfa başına 2N ekstra SELECT) tek sorguda join'e indi. `UserControllerTest.listReturnsUsersWithRolesAndGroups` artık firstName/lastName/enabled da assert ediyor (profile+account fetch).

### RISK-28
**TOCTOU uniqueness → 500 (P2)**
- **Bağlam:** `UserService.create`, `RoleService.create/update`, `GroupService.create/update`, `TenantProvisioningService.validateUnique` — hepsi check-then-save pattern'ı. Concurrent duplicate → DB unique constraint → `DataIntegrityViolationException` → 500 `internal_error` (temiz `*_TAKEN` 400 değil).
- **Karar:** `GlobalExceptionHandler`'a `DataIntegrityViolationException` handler + constraint name → `ErrorCode` map; veya her create try/catch + `BusinessException`.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz D. `GlobalExceptionHandler.handleDataIntegrity` Hibernate `ConstraintViolationException.getConstraintName()`'i çıkarıp substring haritasıyla `*_TAKEN` koduna map'ler (`users_email`→USER_EMAIL_TAKEN vb.); bilinmeyen → `business_error`. **Ana kazanç 500→400 garantisi.** Service `existsBy*` check'leri korundu (defense-in-depth; handler concurrent race'i kapatır). Taşınabilir substring eşleştirme (PG index adları + H2 farkı tolere). `GlobalExceptionHandlerTest` (unit, DB'siz) doğrular.

### RISK-29
**`MethodArgumentTypeMismatchException` → 500 (P1)**
- **Bağlam:** `GET /api/v1/users/not-a-uuid` (malformed UUID) `GlobalExceptionHandler` catch-all'a düşer → 500 `internal_error`. Client hatası, 400 olmalı. `GlobalExceptionHandler`'da handler yok. Ayrıca `ConstraintViolationException` ve `MissingServletRequestParameterException` da eksik.
- **Karar:** `GlobalExceptionHandler`'a bu üç handler'ı ekle (→ 400 `validation_error`).
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz D. `handleTypeMismatch` (MethodArgumentTypeMismatchException — malformed UUID), `handleMissingParam` (MissingServletRequestParameterException), `handleConstraintViolation` (jakarta ConstraintViolationException — defansif, şu an `@Validated` yok) eklendi; hepsi → 400 `validation_error`. ConstraintViolation invalid-value'ları maskeli. `GlobalExceptionHandlerTest` + `UserControllerTest.getMalformedUuidReturns400Not500` doğrular.

### RISK-30
**Verification token plain-text + stale retention (P2)**
- **Bağlam:** `TenantVerificationToken.token` plain-text DB'de (`UUID.randomUUID()`), `LogVerificationSender` log'da. DB sızıntısında unused token replay edilebilir. Expired/used token'lar için purge job yok → `adminPasswordHash` (pepper'lı ama yine de) kalıcı.
- **Karar:** Token hash-at-rest (DB'ye `SHA-256(token)`, lookup hash'le) + scheduled purge job (expired + used) + `adminPasswordHash` consume sonrası null.
- **Durum:** Açık. Refactor Faz E.

### RISK-31
**K-21 + DELETE endpoint test coverage (P1)**
- **Bağlam:** `/api/v1/auth/company/register` (202), `/verify`, `/suggest-subdomain` için HTTP-level test yok — sadece `TenantProvisioningServiceTest` (pure Mockito). `@Pattern`, response contract, 202/200 distinction doğrulanmamış. DELETE endpoint'leri dahil çoğu `{id}/PUT` için 401/403 test eksik (sadece `deleteReturns204` happy path var).
- **Karar:** Controller integration testleri (Faz B test altyapısıyla birlikte).
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz B. `AuthCompanyControllerTest`: `POST /company/register` (202 + contract + `@Pattern` subdomain validation), `POST /company/suggest-subdomain` (200 + Türkçe fold). DELETE 401 testleri (user/role/group controllers). `verify` mutlu-yolu gerçek PG gerektirdiğinden `CrossTenantIsolationTest` ile; token-error kodları `TenantProvisioningServiceTest` ile kapsanır.

### RISK-32
**`PlatformCompanyService.updateStatus` state-machine'siz (P2)**
- **Bağlam:** Her `CompanyStatus` → her `CompanyStatus` geçişi mümkün (TERMINATED→ACTIVE yeniden canlandırma, ACTIVE→PROVISIONING geri alma, PROVISIONING→ACTIVE schema/admin olmadan — login kırılır).
- **Karar:** `CompanyStatus.canTransitionTo()` veya `EnumSet` allowed-transitions; `updateStatus` doğrula, geçersizse `BUSINESS_ERROR`.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz E. `CompanyStatus.canTransitionTo` `EnumSet` tablosuyla: ACTIVE→{SUSPENDED,TERMINATED}, SUSPENDED→{ACTIVE,TERMINATED}; PROVISIONING/TERMINATED terminal (PROVISIONING sadece verify akışıyla biter). `PlatformCompanyService.updateStatus` geçersiz geçişi `business_error` (400) ile reddeder. `PlatformCompanyControllerTest.illegalStatusTransitionReturns400` doğrular.

### RISK-33
**AuditorAware hardcoded "system" authenticated yazımlarda (P2)**
- **Bağlam:** RISK-3 aynı sorun, ama authenticated admin işlemlerinde (`UserService`, `RoleService`, `GroupService`, `changePassword`) gerçek `userId` kullanılmıyor. Signup/provisioning için beklenir ama admin CRUD için değil.
- **Karar:** `AuditorAware` `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` → `CustomUserDetails.getUserId()`, fallback `"system"` (signup background). RISK-3'ü kapatır.
- **Durum:** ÇÖZÜLDÜ (2026-07-24). Refactor Faz E. `MultiTenancyJpaConfig.auditorAwareProvider` SecurityContext → `CustomUserDetails.getUserId()` okur; principal yoksa `"system"` fallback (signup/provisioning/startup). `AuditingTest` her iki yolu da doğrular. [RISK-3](#risk-3)'ü kapatır.

### RISK-34
**Spring Boot 4 deprecated starter POM'ları (P2)**
- **Bağlam:** SB4 modularizasyonu ile bazı starter'lar deprecated ("will be removed in a future release" — resmi migration guide): `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-web` → `spring-boot-starter-webmvc`. Flyway için sadece `org.flywaydb:flyway-core` yerine `spring-boot-starter-flyway` öneriliyor. Ayrıca `HttpMessageConverters` deprecated (SB4), `@JsonComponent`→`@JacksonComponent` (Jackson 3).
- **Karar:** Deprecated starter'ları yenileriyle değiştir; custom `HttpMessageConverters` bean varsa `ServerHttpMessageConvertersCustomizer`'a geçir.
- **Durum:** Açık. Refactor Faz E.

### RISK-35
**Last-admin lockout — hiçbir write path'te self-delete / son-admin koruması yok (P0)**
- **Bağlam:** Bir tenant admin'i kendisini soft-delete etti → tenant'ta sıfır admin-capable user kaldı; yönetecek kimse yok (gerçek olay, DB reset gerektirdi). `UserService.delete/update/setRoles/setGroups`, `RoleService.delete/setPermissions/setParents`, `GroupService.update/delete/setRoles/setMembers` — 11 path'in hiçbirinde aktör-vs-hedef veya son-admin kontrolü yoktu. Yan bulgular: (1) `AuthService.login` `enabled` kontrolü yapmıyordu — disabled user doğru şifreyle token alabiliyordu; (2) `UserService.update(enabled=false)` ve `delete` session revoke etmiyordu — silinen/engellenen kullanıcının access token'ı TTL'ine kadar yaşıyordu.
- **Karar:**
  - **Admin-capable tanımı:** effective role closure'ı (direct + active-group + parent inheritance) içinde `all_permissions=true` rolü bulunan user. Rol adı "Admin" değil (seed konvansiyonu), spesifik permission da değil. Disabled (`enabled=false`) user'lar invariant'a sayılmaz → son AKTİF admin disable edilemez.
  - **Self-delete koşulsuz yasak:** aktör userId == hedef userId → 409 (`self_delete_forbidden`), başka admin olsa bile.
  - Platform-level kurtarma endpoint'i kapsam dışı (ayrı gelecek çalışma).
  - `LastAdminGuard` (`backend/security/`): `assertNotSelf` (SecurityContext aktörü) + `assertActiveAdminExists` (post-mutation; JPQL auto-flush pending değişiklikleri görür). Admin-closure: flag rolleri + `t_role_parents` üzerinden AŞAĞI BFS (`findChildRoleIds` — admin rolünden inherit eden rol de admin-capable) + tek exists sorgusu (`existsEnabledByRoleIds`: enabled + direct/active-group; `@SQLRestriction` soft-deleted eler). Logic-only, migration YOK (RISK-16 tetiklenmedi).
  - **Side-fix 1:** login doğru şifre SONRASI `enabled` kontrolü (enumeration önleme — `auth_account_disabled` 401).
  - **Side-fix 2:** `update(enabled=false)` ve `delete` artık `SessionRevocationService.revokeUser` çağırıyor (token'lar anında düşer).
  - Ordering: guard revoke'dan ÖNCE → reddedilen işlem Redis tarafında refresh-token hasarı bırakmaz; role/group delete'te holder id'leri soft-delete ÖNCESİ çözülür.
  - Bonus (latent bug surfaced): role/group soft-delete, kendisine hâlâ referans veren managed `User.roles`/`User.groups` koleksiyonları nedeniyle flush'ta `TransientPropertyValueException` (500) atabiliyordu. Delete artık önce join satırlarını koleksiyon mutasyonuyla temizliyor (`findUsersByRole`/`findGroupsByRole`/`findGroupMembers`), orphan join satırları da kalmıyor.
  - Frontend: aktörün kendi satırında/sayfasında Delete butonu gizli (`UsersPage`/`UserDetailPage`, `user.id !== currentUserId`); 409 mesajları mevcut global toast ile gelir.
- **Durum:** ÇÖZÜLDÜ (2026-08-15). 324 test yeşil (H2). Yeni `ErrorCode`'lar: `last_admin_required` (409), `self_delete_forbidden` (409), `auth_account_disabled` (401). Controller testleri: son admin delete/disable/role-boşaltma/group-üyelik-çekme → 409; ikinci admin varken → 204; self-delete (başka admin olsa bile) → 409; disabled admin invariant'a sayılmaz; disabled login → 401. Dosya:ref — `security/LastAdminGuard.java`, `UserRepository.existsEnabledByRoleIds`, `RoleRepository.findChildRoleIds`, `UserService.java` (delete/update/setRoles/setGroups), `RoleService.delete`, `GroupService.delete`.

### RISK-36
**RbacSeeder startup privilege escalation (P0)**
- **Bağlam:** `RbacSeeder` her restart'ta `findByRolesEmpty` ile rol'süz tüm kullanıcıları bulup `all_permissions` bayraklı `Admin` rolüne atıyordu (provisioning admin'ini garanti altına alma niyetiyle). Kasıtlı olarak yetkisiz bırakılmış bir kullanıcı, uygulamanın her restart'ında sessizce tam admin yetkisine yükseliyordu. `SystemAdminBootstrapRunner`'daki system admin ataması da bu örtük yola bağımlıydı.
- **Karar:** Seeder startup'ta ASLA kullanıcı rol ataması yapmaz; Admin yalnızca `TenantProvisioningService.createAdminUser` içinden explicit `RbacSeeder.assignAdminTo(user)` çağrısıyla, tenant provisioning'i sırasında verilir. `findByRolesEmpty` repository metodu kaldırıldı (dangling reference yok).
- **Durum:** ÇÖZÜLDÜ (2026-08-16). `RbacSeederTest` regresyonu: seed sonrası rol'süz kullanıcı rol'süz kalır; provisioning yeni admin'e Admin atar. Yan düzeltmeler aynı sette: (1) aktif `lockedUntil` penceresi artık refresh'te de bloklu (`CustomUserDetails.isEffectivelyNonLocked` — önceden kilitli hesap 15 dk boyunca refresh ile yeni access token basabiliyordu); (2) `RedisRefreshTokenStore.revoke` rotasyon zincirini (`rotatedTo`) takip ediyor — rotate edilmiş token ile logout, halef token'ı da öldürüyor (logout↔silent-refresh yarışı). Bilinen açık P2'ler: `InMemoryRefreshTokenStore.revoke` zincir takibi prod paritesinde değil; revoke zincir yürüyüşü Redis tarafında da atomik değil (Lua değil).

---

## Teknik Borç (DEBT-XX)

### DEBT-7
**hashCode() bug (ÇÖZÜLDÜ)**
- **Bağlam:** `BaseEntity` ve `GeneratedIdAuditEntity` `Objects.hash(getClass())` kullanıyor -> aynı tipteki tüm entity'lere aynı hash -> `Set<Role>` gibi koleksiyonlarda çakışma.
- **Karar:** `hashCode()` ID baz alınacak şekilde düzeltilecek (her iki base sınıfta).
- **Durum:** ÇÖZÜLDÜ (Epic 2.0.B). Uygulama: `id == null ? System.identityHashCode(this) : id.hashCode()`. Persist öncesi (id null) identity hash, persist sonrası ID-bazlı. RBAC'dan önce düzeltildi.
- **Konvansiyon:** ID persist sırasında `null→UUID` değiştiği için yeni (transient) entity'yi persist **öncesinde** `HashSet`/`HashMap` anahtarı olarak kullanıp persist sonrası lookup yapmak güvenli değildir. Pratik kullanımda (DB'den yüklenen entity'ler) sorun yok.

### DEBT-10
**provisionTenant transaction'suz (KISMEN ÇÖZÜLDÜ)**
- **Bağlam:** `TenantProvisioningService.provisionTenant()` ve `createAdminUser()` `@Transactional` değil. Kısmi write riski (DDL başarılı, JPA write fail -> yarım tenant).
- **Karar:** Yazma işleri `@Transactional` (method-level) olacak; lookup'larda `readOnly=true`. K-21 ile birlikte çözüldü (`createPendingCompany` + `verifyAndProvision` her ikisi transactional).
- **Durum:** KISMEN ÇÖZÜLDÜ (K-21 ile). `createPendingCompany` tam transactional (yalnız DB write). `verifyAndProvision` `@Transactional` işaretli ama `CREATE SCHEMA` PostgreSQL implicit commit nedeniyle transaction dışına kaçar — kısmi-write recovery idempotency ile sağlanır (`CREATE SCHEMA IF NOT EXISTS`, token `usedAt` guard). Tam transactional DDL PostgreSQL'de mümkün değil.

---

## ID Şeması

- **K-XX:** Mimari karar (Architecture decision). Stratejik yön.
- **RISK-XX:** Tanımlanmış risk — azaltıcı eylem gerekli (mitigation).
- **DEBT-XX:** Teknik borç — bilinen eksiklik, refactor bekliyor.

Yeni keşfedilen işler sonraki boş ID'yi alır. ID'ler değişmez. İptal edilen karar durumu "İptal" olarak güncellenir, silinmez (geçmiş iz için).
