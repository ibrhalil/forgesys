# Karar Kayıtları (Decision Log)

> Bu dosya ForgeSys'un mimari/teknik kararlarının (K-XX), risk kayıtlarının (RISK-XX) ve teknik borçlarının (DEBT-XX) tek merkezi. ID'ler karar verildiği sırayla artar, değişmez.
>
> **Kayıt boşluğu notu:** Bu dosyada bulunmayan ID'ler (ör. K-1..K-14, K-17, K-31, RISK-1/2/4..9/11/12, DEBT-1..6/8/9) ilk yaygın review'dan (2026-07) öncesine aittir ve back-fill edilmedi — yeni kayıtlar için **KULLANILMAZ**; kayıtlar mevcut en yüksek ID'den devam eder.
>
> Uygulama detayları (kod konvansiyonları, endpoint kataloğu, gotcha'lar) modül `AGENTS.md`'lerinde yaşar — bu dosya kararın **bağlam + sonuç**'unu taşır.

## Format

Her kayıt: **Bağlam** (problem/ikilem) → **Karar** (ne seçildi, neden) → **Durum** (Uygulandı / Planlandı / İptal / Açık) → **Etki** (dikkat edilecekler).

---

## Dondurulmuş Kararlar (tekrar tartışılmaz)

Standart haline gelmiş kararlar. Yeni gereksinim bunlardan biriyle çelişirse = yeni K-XX kaydı gerekir.

| # | Karar | Kaynak |
|---|-------|--------|
| 1 | Multi-tenancy = schema-per-tenant | ARCHITECTURE.md |
| 2 | Registry'ler kodda (enum) — DB katalog tablosu yok | K-16 |
| 3 | Modül migration'ları `db/migration/module/<key>` + per-module Flyway history | K-16 |
| 4 | Auth = RS256 JWT cookie + opaque refresh (Redis, rotasyon + reuse detection) | K-34 |
| 5 | Revoke = `tokenInvalidBefore` (user-scoped) + `jti` blacklist (granular) | RISK-21 + K-34 |
| 6 | RBAC = `@PreAuthorize` + `{module}:{resource}:{action}` namespace | K-26 |
| 7 | Authority çözümlemesi = DB-driven (direct + active group + transitive parent) | K-26/K-35 |
| 8 | Admin = `all_permissions` flag (implicit süper-kullanıcı) | K-35 |
| 9 | LastAdminGuard write path'lerde (son aktif admin kaybedilemez) | RISK-35 |
| 10 | Plan/module limitleri = soft-block 403, veri asla gizlenmez | K-15/K-16 |
| 11 | Wire contract = `PageResponse` + `ApiErrorResponse` + stable `ErrorCode` | K-37 |
| 12 | Sort/filter = whitelist + JPA metamodel sabitleri | backend/AGENTS.md |
| 13 | Frontend = data-driven routing + RequirePermission + TanStack Query + Zustand | frontend/AGENTS.md |
| 14 | Auth transport = httpOnly cookie + transparent refresh | K-34 |
| 15 | Migration sürümleme = `V1.x` baseline + `V2+` (K-36 sonrası) | K-36 |
| 16 | Test stratejisi = H2 default + gated gerçek PG/Redis IT | RISK-20 |
| 17 | JSONB = düz `String` + `columnDefinition="jsonb"` (hypersistence-utils yok) | K-15 |
| 18 | DTO mapping = manuel `toResponse` (MapStruct iptal) | K-37 |
| 19 | Şifre = Peppered BCrypt(12) | K-23 |
| 20 | Audit = `@AuditLog` AOP + append-only DB trigger + delta kaydı | K-19/K-27 |
| 21 | Speculative kod eklenmez; ölü kalan kaldırılır (planlar burada yaşar, kodda değil) | K-38 |
| 22 | API konvansiyon: `PageResponse` standart (belgeli pick-list istisnaları), tek `/me`, DELETE→204, controller adı = path namespace, DTO record (`Map` dönüş yok) | K-37 |
| 23 | Frontend: strict TS + yeni feature'da test zorunlu; liste-sayfa scaffold'u `useListPageState` üzerinden | K-39 |
| 24 | Startup runner'ları projection yükler; paylaşılan çözümleme zincirleri tek kaynakta yaşar | K-40 |
| 25 | Proje tipi kataloğu aktif modül registry'sinden türer; içerik varken tip değişimi yasak (409); link katmanı talep-kapılı | K-45 |

---

## Mimari Kararlar (K-XX)

### K-15
**Custom App Builder (Notion-style)**
- **Bağlam:** Tenant'lar sabit built-in modüller dışında kendi mini-uygulamalarını (esnek veri modeli + view'ler) yaratabilmeli.
- **Karar:** JSONB EAV modeli — `t_apps`/`t_app_properties(config jsonb)`/`t_app_records`/`t_app_record_values(value jsonb, GIN)`/`t_app_views(config jsonb)`. Property tipleri TEXT/NUMBER/SELECT/DATE/USER/RELATION (FORMULA erteli — yaratma reddedilir). View config structured JSON DSL (serbest expression dili yok → injection yüzeyi yapısal olarak kapalı). JSONB search native PG (`AppRecordSearchExecutor` — PG-only, H2'de koşmaz). Plan limitleri `PlanDefinition` registry'sinde, soft-block 403.
- **Durum:** UYGULANDI (backend 2026-08-22; UI K-42, 2026-08-23).
- **Etki:** Value satırları soft-delete'siz (clear = satır silinir). JSONB mapping düz String + `stringtype=unspecified`. Doğrulama: `AppBuilderIT` (gated gerçek PG).
- **K-45 amend:** App'ler `APPS`-tipli proje konteynerinde yaşar (`t_apps.project_id`); App ağacı topolojisi (Property/Record/Value/View) ve plan limitlerinin tenant-seviyesi sözleşmesi değişmez.

### K-16
**Plan Bazlı Modül Aktivasyonu**
- **Bağlam:** Tüm tenant'lar tüm modülleri kullanmamalı; Free/Pro/Enterprise planları erişimi belirlemeli.
- **Karar:** `t_plans`/`t_subscriptions`/`t_tenant_modules` (public `V2`). Registry kodda (`ModuleDefinition` + `PlanDefinition` enum — DB katalog tablosu YOK). Modül migration'ları `db/migration/module/<key>` altında (core `tenant/` ağacı DIŞINDA — Flyway scan recursive) + `flyway_schema_history_mod_<key>` bağımsız versyonlama. Transaction split: aktivasyon kaydı caller tx'ine katılır (FK-deadlock önleme), yalnız permission seed `REQUIRES_NEW`. Signup → FREE + default modüller; `ModuleSyncRunner` startup'ta mevcut tenantlara backfill/re-sync.
- **Durum:** UYGULANDI (2026-08-22). Finansal taraf (ödeme, plan değişimi, deaktivasyon) Faz 6.

### K-18
**Nginx ertelendi**
- **Bağlam:** Orijinal plan Faz 1.5'te 3-container separation + Nginx; kullanıcı tam RBAC platformunu önceliklendirdi.
- **Karar:** Faz 1.5 Faz 2 sonrasına ertelendi; K-33 ile uygulama **proje %90 tamamlanana kadar** uzatıldı. Vite proxy dev'i karşılıyor; prod tek app container.
- **Durum:** Uygulandı (erteleme).

### K-19
**3 Katmanlı Log**
- **Bağlam:** Kurumsal platform için farklı amaçlara hizmet eden ayrı log katmanları gerekli.
- **Karar:** (1) `t_audit_logs` (admin aksiyon; `@AuditLog` AOP ile yazılır; append-only trigger + yetki değişim delta JSON) (2) `t_login_history` (her login denemesi, success + failure; append-only) (3) `t_request_logs` (request metadata + high-risk yollarda maskeli body) + MDC traceId (`X-Request-Id`). Read side: `/audit-logs`, `/login-history`, `/request-logs` (`iam:audit:read`). Yazılar `REQUIRES_NEW` + best-effort.
- **Durum:** UYGULANDI (core 2026-07-27; request-logs katmanı K-27 ile 2026-08-23).

### K-20
**Admin/User/Log UI önceliği**
- **Karar:** Built-in modül UI'ları beklemeden admin/user yönetimi UI'ı (Epic 4.0.B) backend Faz 2'den hemen sonra gelir; Faz 4 core stack bu epic'te kurulur.
- **Durum:** UYGULANDI (2026-08; request-logs sayfası 2026-08-23).

### K-21
**İki Fazlı Tenant Signup**
- **Bağlam:** Open endpoint'te ağır DDL (schema + Flyway) + subdomain squatting riski.
- **Karar:** Faz 1 `register` — hafif (PROVISIONING Company + `TenantVerificationToken`; admin credential'ları token'a pre-hash gömülür) + doğrulama linki maili. Faz 2 `verify` — kullanıcının linke tıklamasıyla senkron: CREATE SCHEMA + Flyway + admin user → ACTIVE. `suggest-subdomain` (Türkçe-aware slug). Bootstrap yolu: `provisionSystemTenant` (K-24 — auto-verify, mail yok).
- **Durum:** UYGULANDI.
- **Etki:** Token consume atomic conditional UPDATE (RISK-25). `CREATE SCHEMA` implicit commit → DEBT-10 kısmi. Mail gönderimi şu an log (SMTP Faz 5'te).

### K-22
**Tenant Domain Handoff / Schema Archival**
- **Bağlam:** Aboneliği kapanan şirketin subdomain/schema değeri yeni kayıt olana serbest kalmalı; eski veri kaybolmamalı.
- **Karar:** Katman 1 — soft-delete partial index ile silinen değerler yeniden kullanılabilir (RISK-17, uygulandı). Katman 2 — fiziksel arşiv (`ALTER SCHEMA tenant_X RENAME TO tenant_X_archived`) + reaktivasyon onayı; platform admin tooling / Faz 6 kapsamı.
- **Durum:** PLANLANDI (katman 1 uygulandı).

### K-23
**Global Password Pepper**
- **Bağlam:** DB leak tek başına hash kırımına izin vermemeli. Per-tenant pepper ek key yönetim riski getirir, tehdit modeline ek katmaz → global.
- **Karar:** HMAC-SHA256 pre-hash (OWASP) + BCrypt(12); `{sf-peppered}` marker; legacy pepper'sız hash'ler geçerli + ilk login'de lazy rehash. Pepper env/config'ten; boş → startup fail-fast.
- **Durum:** UYGULANDI.
- **Etki:** Pepper rotasyonu desteklenmez (rotasyon = tüm pepper'lı hash'ler resetlenir; gerekirse özel akış tasarlanır — runbook ROADMAP'te). Pepper asla loglanmaz/commit edilmez.

### K-24
**System Tenant Bootstrap**
- **Karar:** `SystemAdminBootstrapRunner` startup'ta rezerve `system` tenant'ını + platform admin'ini idempotent provision eder (`provisionSystemTenant` auto-verify). Normal signup `system` subdomain'ini alamaz. Bootstrap hatası startup'ı durdurmaz (log + swallow).
- **Durum:** UYGULANDI.

### K-25
**Platform Admin Namespace**
- **Bağlam:** Tenant-scoped `iam:*` dışında cross-tenant işlemler (tüm tenant listesi, SUSPEND/TERMINATE) gerekli.
- **Karar:** `platform:company:read/write` namespace + `/api/v1/platform/companies`. `executeWithoutTenantContext` — **tek sanctioned** cross-tenant okuma yolu (başka yerde çoğaltılmaz).
- **Durum:** UYGULANDI. Bilinen zayıflık: RISK-18.

### K-26
**RBAC Enforcement (Method Security)**
- **Karar:** `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('{module}:{resource}:{action}')")` tüm admin endpoint'lerinde; self-service `/users/me/**` authenticated-only. Yetkisiz → 403 uniform shape.
- **Durum:** UYGULANDI.

### K-27
**Audit & Log Genişletmesi**
- **Karar:** (1) Başarısız login denemeleri de `t_login_history`'e yazılır. (2) High-risk endpoint'lerde request body maskeli loglanır (`forgesys.audit.high-risk-paths` config; `password`/`token`/`secret` → `[REDACTED]`). (3) `@AuditLog` AOP — explicit auditService çağrılarının yerine. (4) Anomaly detection passif (block değil, alert — K-29 besler). (5) Approval workflow (`t_pending_actions`, iki-admin onayı, config-driven).
- **Durum:** KISMEN UYGULANDI (2026-08-23): 1-3 + append-only trigger + delta kaydı + request-logs tablosu/endpoint/UI tamam. Kalan (bilinçli erteli, LOW): 4-5.

### K-28
**Session Management & Remote Revoke**
- **Karar:** Aktif session'lar Redis'te (her refresh token = session kaydı: device/ip/loginAt). Self (`/users/me/sessions`) + admin (`/users/{id}/sessions`) list/revoke; tenant-genel `/api/v1/sessions`; max concurrent limit (`forgesys.security.max-sessions`, en eski düşürülür). Revoke access token'ı anında düşürür (`tokenInvalidBefore` stamp). `t_sessions_log` tablosu **İPTAL** (`t_login_history`/`t_audit_logs` ile örtüşme).
- **Durum:** UYGULANDI (2026-07-30, K-34 altyapısı üstünde).

### K-29
**Notification Subsystem**
- **Bağlam:** Güvenlik olayları (şüpheli login, yeni cihaz, şifre/rol değişimi, bulk delete, session revoke) bildirim gerektiriyor.
- **Karar:** `NotificationService.send(userId, type, payload)` + iki kanal: in-app (`t_notifications`, polling; WebSocket Faz 5+) + mail (K-21 sender infra'sını paylaşır). Type catalog (SUSPICIOUS_LOGIN, PASSWORD_CHANGED, ROLE_ASSIGNED/REVOKED, SESSION_REVOKED_BY_ADMIN, ...). Per-user kanal tercihleri (`t_notification_preferences`). Template'ler `infra/templates/` (TR/EN).
- **Durum:** PLANLANDI. In-app kanalı bağımsız yapılabilir; mail SMTP'ye (Faz 5) bağlı. K-27 anomaly + K-28 revoke tetikleyicidir.

### K-30
**Activity Feed**
- **Bağlam:** Audit log admin forensics için; normal kullanıcıya "Ali X grubunu oluşturdu" tarzı insan-okur akış gerekli.
- **Karar:** Audit log üstünden **türetme** (view/sorgu — ayrı tablo erteli); `{action}_{entity}` template map (i18n) ile text üretimi; visibility scope (public/team/private); `/api/v1/activities` (sayfalı, filtreli).
- **Durum:** PLANLANDI.

### K-32
**`email_domain` kaldırıldı**
- **Bağlam:** `t_companies.email_domain` tek string — çoklu domain (holding) ve domain'siz kayıt (klüp) senaryolarını karşılamıyordu.
- **Karar:** Kolon + partial index DROP; entity field'ı kaldırıldı. 1:N org domains tablosu (custom domain doğrulama / LDAP-SSO ön koşulu) planlandıysa da **K-38 ile speculative kaldırıldı** — email-domain self-register akışı gelirse kendi `V2` migration'ıyla döner.
- **Durum:** UYGULANDI (kolon DROP; tablo K-38 ile kaldırıldı).

### K-33
**Nginx Gateway Topology**
- **Bağlam:** VPS'te shared Nginx gateway (ayrı repo) birden fazla projeyi host edecek; ForgeSys `*.forgesys.app` wildcard TLS gerektiriyor; managed CDN (Cloudflare vb.) kullanılmayacak.
- **Karar (uygulama %90 sonrası):** Shared gateway ayrı repo + external Docker network (`gateway-net`); her proje `ports:` → `expose:` + sabit container name. Wildcard Let's Encrypt **DNS-01** (HTTP-01 wildcard desteklemez; certbot DNS plugin — sağlayıcı açık uç). Route: Host header korunur (`TenantFilter` subdomain çözer); `/actuator/health` allow; `limit_req` app-level rate limit ile birlikte. Security headers Nginx'te (CSP backend'te — duplicate yok); HTTP→HTTPS redirect + HSTS.
- **Durum:** PLANLANDI. Açık uçlar (uygulama anında): DNS sağlayıcı/plugin, `nginx-gateway/` repo, `infra/nginx/` şablonları, cert renewal reload hook. `%90` ölçütü: Faz 2+3+4 ana akışları (Faz 5/6 bekleyebilir).

### K-34
**Redis Refresh Token + Rotation + Reuse Detection**
- **Bağlam:** Access token kısa ömürlü/stateless; uzun refresh + rotasyon + per-session logout gerekli; dead `t_refresh_tokens` tablosu vardı.
- **Karar:** Opaque refresh token, Redis'te **SHA-256 hash-at-rest**; atomik Lua rotasyon + reuse detection (ROTATED token tekrar sunulursa tüm session'lar revoke + `tokenInvalidBefore`). Transport: ayrı httpOnly cookie (`Path=/api/v1/auth`). `POST /auth/refresh` authorities'ı DB'den re-resolve eder (taze yetkiler + locked/disabled re-check). Per-session logout: access `jti` blacklist (Redis, TTL = access ömrü). Store impl'leri `@Profile`-split (Redis dev/prod, InMemory test — Docker'sız build).
- **Durum:** UYGULANDI (2026-07-30). `t_refresh_tokens` tablosu K-36 squash'ında silindi.
- **Etki:** Aynı tokenla eşzamanlı iki refresh reuse tetikler (grace window yok — client refresh'i serialize eder). Redis kesinti davranışı bilinçli: RISK-36.

### K-35
**`all_permissions` Flag**
- **Bağlam:** Runtime eklenen permission'lar Admin'e ulaşmıyordu; rol kurarken tüm permission'ları tek tek seçmek yorucuydu.
- **Karar:** `t_roles.all_permissions` — flag'li rol tenant'taki tüm permission'ları implicit taşır (`PermissionRepository.findAllNames`, parent-closure sonrası çözümlenir → parent'ı all-permissions olan rol de all-permissions). `RbacSeeder` Admin'i flag'li seed eder (explicit grant satırı yok → permission silme `in_use` bloğuna takılmaz); `PUT /roles/{id}/permissions` `{all:true}` kısayolu. Runtime permission create/rename → holder token revoke (immediacy).
- **Durum:** UYGULANDI (2026-07-31).

### K-36
**Pre-1.0 Migration Squash**
- **Bağlam:** Deploy edilmiş DB yokken migration geçmişi `V1..V8` birikmişti.
- **Karar:** Tüm pre-1.0.0 migration'lar alan-bazlı `V1.x` baseline ailesine indirildi (public: `V1`, `V1.1`; tenant: `V1`..`V1.3`). Ölü `t_refresh_tokens` + `RefreshToken` entity silindi; `version BIGINT NOT NULL DEFAULT 0` gömüldü; `baselineOnMigrate` kaldırıldı (fresh-DB-only). Yeni migration'lar `V2+`.
- **Durum:** UYGULANDI (2026-08-22). Local DB'ler sıfırlandı (checksum değişti — README troubleshooting).

### K-37
**API/wire Tutarlılık Geçişi**
- **Bağlam:** 85 endpoint'te biriken konvansiyon sapmaları: bölünmüş sayfalama, çift `/me`, DELETE 200+body, session endpoint'lerinin 4 controller'a dağılması, `Map` dönüş.
- **Karar:** Tek seferlik geçiş (K-36 penceresi, deprecasyon yükü yok): `PageResponse` standart (belgeli `List` istisnaları: app properties/views + session listeleri), tek `/me` (`/users/me`), DELETE→204, controller adı = path namespace, class-level `@RequestMapping`, DTO record kuralı (MapStruct kökten iptal). Springdoc bu geçiştEN SONRA (K-41).
- **Durum:** UYGULANDI (2026-08-22).

### K-38
**Ölü/Speculative Kod Politikası**
- **Karar:** "İleride lazım olur" düşüncesiyle kod taşınmaz; planlar bu dosyada yaşar. Kaldırılanlar: `OrganizationDomain` + `t_organization_domains`, `OwnershipGuard`/`Ownable`, `Company.dbRole`, `User` token kolonları ×4, frontend `resendVerification`. Enum-değer düzeyi speculative'ler (`ProjectType.NOTES`, `PropertyType.FORMULA`) kalır. (K-45: `NOTES` anlamlı hale geldi, `APPS` eklendi — bu istisna kapandı; `FORMULA` kalır.)
- **Durum:** UYGULANDI (2026-08-22; baseline V1 checksum değişti → local DB reset).

### K-39
**Frontend Kalite Gate'leri**
- **Karar:** `tsconfig.app.json` `strict: true`; Vitest + RTL (yeni frontend feature'ı **testsiz merge edilmez**); 7× kopya liste-scaffold → `useListPageState` hook; i18n dictionary kalır (data'dır).
- **Durum:** UYGULANDI (2026-08-22). Node 20 pin'i test dep sürümlerini belirler (jest-dom 6 / jsdom 29 — Node ≥22 isteyen sürümler reddedilir).

### K-40
**Startup Projection + Tek Kaynak Çözümlemeler**
- **Karar:** Startup runner'ları entity değil projection yükler (`findAllTenantSchemas` — id+schemaName+status). Paylaşılan çözümleme zincirleri tek kaynakta: plan zinciri `PlanLimitService.tryActivePlan`, cookie helper `JwtCookieProperties`. InMemory/Redis rate-limiter refill çifti **bilinçli kalır** (Docker'sız test stratejisi; parity test ile korunur).
- **Durum:** UYGULANDI (2026-08-22; davranış değişikliği yok).

### K-41
**springdoc-openapi**
- **Karar:** springdoc **3.1.0** (SB4/Jackson 3 hattı — 2.x Jackson 2/SB3). Dev/test açık (`/swagger-ui.html`, `/v3/api-docs`), prod kapalı (`springdoc.api-docs.enabled=false` + `swagger-ui.enabled=false` → endpoint'ler unregister). `OpenApiConfig` global `cookieAuth` scheme (apiKey/cookie).
- **Durum:** UYGULANDI (2026-08-22).

### K-42
**App Builder UI**
- **Karar:** Plan limit sayıları backend registry'sinden (`GET /apps/plan-limits`) — frontend'e sabit kopya YOK. Record edit PATCH partial-merge diff'iyle (yalnız değişen key'ler; required-clear bloklu). Bilinçli sadeleştirmeler: drag-drop YOK (select/move mover), expression editor YOK (structured DSL), CALENDAR'da kayıt aksiyonu YOK. Filter/sort client-side (`GET /records` tek sayfa, cap 1000 — `records/search` PG-only).
- **Durum:** UYGULANDI (2026-08-23).

### K-43
**Metrics Expose (Micrometer + Prometheus)**
- **Karar:** `micrometer-registry-prometheus` (BOM-managed). Exposure: dev/test `health,info,metrics,prometheus` same-port (scrape auth'suz); prod `health,info,prometheus` + **ayrı management portu 8081** expose-only (management child context'te security chain uygulanmaz → internal ağdan auth'suz scrape). Custom gauge: `forgesys.tenants.active`. OTel tracing erteli (K-33 ile birlikte değerlendirilecek).
- **Durum:** UYGULANDI (2026-08-23).

### K-44
**Notes Modülü**
- **Karar:** Standalone (`/api/v1/notes` + `/note-categories`) + tenant-shared görünürlük (pm deseni; ABAC erteli). Markdown editör: `react-markdown` + `remark-gfm`, `rehype-raw` BİLİNÇLİ yok → raw HTML render edilmez (XSS yüzeyi kapalı). Modül yapısı APPS deseni (`module/notes/V1__notes.sql` + bağımsız history). Default-aktif (`pm,apps,notes`; test fallback `pm` — H2'de modül migration örtük koşmaz). Bilinçli yapılmayanlar: WYSIWYG, full-text search (tsvector), not-başına unique başlık.
- **Durum:** UYGULANDI (2026-08-23).
- **K-45 revert:** Yerleşim standalone'dan project-scoped'a döndü — notlar `NOTES`-tipli konteynere çapalanır (`module/notes/V2`); flat endpoint'ler cross-container filtre görünümü olarak kalır (`?projectId=`). Tenant-shared görünürlük, markdown ve modül yapısı kararları yerinde.

### K-45
**Typed Project Container + 5 Katmanlı Sentez**
- **Bağlam:** pm/apps/notes üç ayrı üst-düzey yüzey olarak duruyordu; oysa ilk tasarım ("type-driven lightweight module system" — `ProjectType.NOTES` placeholder) modülleri proje *tipi* olarak düşünmüştü. Hedef: Jira (tip = davranış şablonu) ve Notion (esnek içerik) sentezi, üzerine kendi genişletilebilirlik eksenimiz. Notion/ClickUp/Jira/Linear/Odoo incelemesi: her ürün mimarinin farklı bir katmanında en iyi → sentez katmanlı kurulur.
- **Karar:** **5 katmanlı referans mimari** (katman = sorumluluk; taahhüt seviyeleri aşağıda):
  1. **Konteyner** (Jira + ClickUp): `Project` = abstract typed container; türsüz var edilemez (`project_type NOT NULL` zaten vardı). `TASKS | NOTES | APPS`; dual persona (TASKS → Jira-tarzı iş yönetimi, NOTES/APPS → Notion-tarzı workspace). `parent_project_id` nullable self-FK — hiyerarşi derinliği kullanıcı tercihi, sistem kısıtı değil (ClickUp'ın sabit 5 katman tuzağı yok). Tip yalnızca içerik davranışını belirler; yönetim/config sahipliği anlamı taşımaz (katman 5).
  2. **İçerik** (Odoo): her içerik tipi installable modülün arkasında; **tip kataloğu aktif modül registry'sinden türer** (`ModuleDefinition.projectType`: PM→TASKS, NOTES→NOTES, APPS→APPS; `GET /api/v1/projects/types`). Modül deaktif → tip seçilemez, o tipteki mevcut projeler read-only. Yeni built-in modül = sıfır model değişikliğiyle yeni proje tipi. Task project-scoped (hazır); **Note konteynere doğrudan çapalanır** (kategori `ON DELETE SET NULL` olduğundan kategori bağı yeterli değil; kategori opsiyonel, verilirse aynı projede olmalı); App `APPS`-koleksiyon konteynerine çapalanır (bir projede N app).
  3. **Görünüm** (Linear): kayıtlı görünümler konteynere sekme olarak eklenir — AppView'in *DSL konsepti* yeniden kullanılır, *tablo soyutlaması* genelleştirilmez (erken soyutlama = kırılganlık). Sıradaki artış (Faz 2), taahhütsüz.
  4. **Bağlantı** (Notion): `t_links` polymorphic link katmanı (`source_type/source_id + target_type/target_id`) — **talep-kapılı**: gerçek çapraz-tip bağlama ihtiyacı doğmadan inmez (#21). Dondurulmuş şartları: FK YOK (polymorphic FK = kırılgan), yazım-anı varlık kontrolü, çift yönlü kompozit indeks + çift yönde sayfalama, source purge'u ile aynı transaction'da öksüz temizliği, trigger/async çapraz-modül senkron YASAK.
  5. **Yönetim ekseni** (Jira team/company-managed): **non-goal** — proje-lokal vs tenant-global yapılandırma sahipliği bu mimaride yer almaz.
  **Faz 1 taahhüdü (katman 1+2):** `tenant/V3` (`parent_project_id` + `is_default` + tip başına partial unique default); `module/notes/V2` + `module/apps/V2` (`project_id` NOT NULL FK + backfill). Sıkı geçiş: her not/app bir konteynerde yaşar; tip başına tek "Genel" default konteyner (modül aktivasyonunda idempotent ensure). Nested API TaskController deseni (`/projects/{id}/notes`, `/projects/{id}/note-categories`, `/projects/{id}/apps`); flat `/notes` `/apps` cross-container filtre görünümü olarak kalır (`?projectId=` — backward compat). Guard'lar: içerik varken tip değişimi 409 `project_type_change_forbidden`; parent döngüsü 409 (yukarı zincir yürüyüşü, derinlik sınırı); default konteynerin tipi/parent'ı değiştirilemez. Plan limitleri tenant-seviyesinde kalır (proje başına değil). Faz 1'de COUNT/badge/dashboard agregası YOK (şişme kaynağı).
- **Durum:** UYGULANDI (Faz 1, 2026-08-23: `tenant/V3` + `module/notes/V2` + `module/apps/V2` + tip kataloğu/aktivasyon kapısı/nested API'ler + üç yönlü proje detay UI'ı. Faz 2 görünüm sekmeleri ve Faz 3 `t_links` taahhütsüz yöndür; Faz 1 sırasında benimsenen yan karar: proje isim benzersizliği TİP BAZLI oldu — `uk_projects_type_name (project_type, name)` — böylece NOTES/APPS defaultları aynı "Genel" adını taşıyabilir).
- **Etki:** Migration sıralama güvencesi: `TenantMigrationRunner` (`@Order(2)`) core'u, `ModuleSyncRunner` modülleri koşturur → `tenant/V3` her zaman `module/*/V2`'den önce iner; module V2 `is_default`'a güvenle başvurabilir. Bağımlılık yönü tek yönlü: içerik repoları konteyneri bilir; görünüm/link katmanları konteyner+içeriği bilir, tersi asla (cyclic dependency yasağı katmanlararasında da geçerli). `ProjectType.NOTES` (K-38 istisnası) anlamlanır; `APPS` eklenir. Üst nav `/notes` `/apps` girdileri cross-container görünüm olarak yaşar (project-first navigasyonla çelişmez).

### K-46
**CI Path-Based Job Filtreleme**
- **Karar:** CI'a `changes` job eklendi (saf git-diff, üçüncü parti action yok): PR'da hedef branch ucuna üç-nokta diff, push'ta `event.before` diff'i. Job-level `if` gate'leri: `backend`+`integration` → `backend|common|persistence/**` + kök `pom.xml`/`mvnw`/`ci.yml`; `frontend` → `frontend/**` + `.npmrc`/`.nvmrc`/`ci.yml`; `docker` → backend||frontend||`Dockerfile`/`.dockerignore`/`docker-compose*` (docs-only push'ta imaj basılmaz). Base çözülemezse (yeni branch, force push) fail-safe: tüm dosyalar değişmiş sayılır. `integration` her backend değişikliğinde koşmaya devam eder (tenant izolasyonu kritik).
- **Durum:** UYGULANDI (2026-08-24).
- **Etki:** Workflow-level `on.paths` BİLİNÇLİ kullanılmadı — job-level skip required check'ler açısından success sayılır, workflow-level skip pending'te asılı kalır. `docker` `!cancelled()` + failure/cancelled guard'larıyla skip-propagation'a karşı korunur. Docs/infra-only değişiklikler hiçbir job tetiklemez.

---

## Risk Kayıtları (RISK-XX)

### RISK-3
**AuditorAware hardcoded "system"**
- **Durum:** ÇÖZÜLDÜ (RISK-33 ile). SecurityContext userId; signup/provisioning/startup `"system"` fallback (beklenen durum).

### RISK-10
**`@Async` thread'lerde TenantContext taşınmaz — AÇIK**
- **Bağlam:** `TenantContext` ThreadLocal; `@Async` yeni thread'de kaybolur.
- **Karar:** `TaskDecorator` (TenantContext + SecurityContext propagation) ilk async tüketici (audit/email) ortaya çıktığında eklenir. Şu an `@Async`/`@EnableAsync` yok.

### RISK-13
**BCrypt strength**
- **Durum:** ÇÖZÜLDÜ. BCrypt(12); legacy strength-10 hash'ler self-describing olduğundan geçerli, lazy migrate.

### RISK-14
**oauth2-resource-server auto-config filter**
- **Durum:** ÇÖZÜLDÜ. Auto-config jwt filter AKTİF EDİLMEZ (`.oauth2ResourceServer()` çağrılmaz); custom `JwtAuthenticationFilter` (revoke kontrolleri için gerekli).

### RISK-15
**DateTimeProvider**
- **Durum:** ÇÖZÜLDÜ. Custom UTC `DateTimeProvider` (`MultiTenancyJpaConfig`).

### RISK-16
**Yeni tenant migration mevcut tenantlara uygulanmaz**
- **Durum:** ÇÖZÜLDÜ. `TenantMigrationRunner` startup'ta tüm şemalara migrate (per-tenant try/catch). Yeni tenant migration'ları `V2+` olarak düşer, runner otomatik uygular.

### RISK-17
**Soft-delete + UNIQUE çakışması**
- **Durum:** ÇÖZÜLDÜ. Pattern: `CREATE UNIQUE INDEX ... WHERE is_deleted = false` tüm soft-delete entity'lerde (baseline `V1.x` içinde). Join tabloları + `GeneratedIdAuditEntity` normal UNIQUE.

### RISK-18
**`platform:*` permission'ları tüm tenantlara seed ediliyor — AÇIK**
- **Bağlam:** `RbacSeeder` Admin'e tüm katalogu verir; liste `platform:company:*` içerir → her tenant Admin'i teorik olarak platform endpoint'lerine yetkili (pratikte TenantFilter + public şema erişimi sınırlandırır — defense-in-depth açığı).
- **Karar:** `platform:*` yalnızca system tenant'a seed edilecek şekilde daraltılacak (`IAM_PERMISSIONS` + `PLATFORM_PERMISSIONS` split).
- **Durum:** Açık — hardening epic'inde.

### RISK-19
**JWT tenant claim doğrulanmıyor (P0, cross-tenant escalation)**
- **Durum:** ÇÖZÜLDÜ. `JwtAuthenticationFilter` token `tenant` claim'i ile `TenantContext` eşleşmezse context temizler (→401); principal şemayı claim'den değil context'ten alır. Gerçek çapraz-tenant doğrulaması: `CrossTenantIsolationTest` (gated PG).

### RISK-20
**Cross-tenant isolation testi yok (P0)**
- **Durum:** ÇÖZÜLDÜ. Gated Testcontainers IT'ler (gerçek PG + Redis): `CrossTenantIsolationTest`, `ModuleActivationIT`, `AppBuilderIT`, `RedisRefreshTokenIT` — varsayılan build Docker'sız, CI'da integration job'unda açık.

### RISK-21
**`tokenInvalidBefore` kontrol edilmiyor (P1)**
- **Durum:** ÇÖZÜLDÜ. Filter her authenticated request'te tek-kolon projection lookup; `iat < tokenInvalidBefore` → 401 (saniyeye floor — hızlı re-login korunur). Set noktaları: password change/reset, privilege-change revoke (Faz IAM 1), lockout, disable/delete.

### RISK-22
**Brute-force koruması yok (P1)**
- **Durum:** ÇÖZÜLDÜ. Login-scoped lockout (5 deneme/15dk, 423) + lock anında `tokenInvalidBefore`; kilitli hesap refresh de basamaz. Ek katman: app-level rate limiting (Redis token-bucket, `/auth/login` + `/auth/company/verify` + `/auth/refresh`); edge `limit_req` K-33.

### RISK-23
**Prod RSA key sessiz ephemeral (P1)**
- **Durum:** ÇÖZÜLDÜ. Prod profilinde key yoksa fail-fast (`IllegalStateException`).

### RISK-24
**Access cookie `Secure` değil (P1)**
- **Durum:** ÇÖZÜLDÜ. `application-prod.yaml` `jwt.cookie-secure: true`.

### RISK-25
**Token consumption race (P1)**
- **Durum:** ÇÖZÜLDÜ. `claimToken` conditional UPDATE (`SET usedAt WHERE token AND usedAt IS NULL`, 0 row → `TENANT_TOKEN_ALREADY_USED`) — H2+PG portable, PESSIMISTIC_WRITE tercih edildi.

### RISK-26
**Mid-tx TenantContext switch (P1)**
- **Durum:** ÇÖZÜLDÜ. `createAdminUser` `REQUIRES_NEW` + self-proxy; `setCurrentTenant` çağrıdan ÖNCE (resolver session açılışında okur). Gerçek PG'de doğrulandı.

### RISK-27
**N+1 (`findAll` profile/account) (P1)**
- **Durum:** ÇÖZÜLDÜ. EntityGraph setleri (liste + `findById`'lar) — user listesi artık `UserDirectoryView` read model üzerinden (N+1 yapısal olarak yok).

### RISK-28
**TOCTOU uniqueness → 500 (P2)**
- **Durum:** ÇÖZÜLDÜ. `DataIntegrityViolationException` handler + constraint-name substring map → 400 `*_TAKEN`; bilinmeyen → `business_error`. Service `existsBy*` check'leri defense-in-depth olarak kalır.

### RISK-29
**Malformed param → 500 (P1)**
- **Durum:** ÇÖZÜLDÜ. `MethodArgumentTypeMismatchException` / `MissingServletRequestParameterException` / `ConstraintViolationException` → 400 `validation_error`.

### RISK-30
**Verification token plain-text + stale retention (P2) — AÇIK**
- **Bağlam:** `TenantVerificationToken.token` plain-text DB'de; unused token DB leak'inde replay edilebilir; expired/used token purge yok; `adminPasswordHash` consume sonrası kalıyor.
- **Karar:** Token hash-at-rest (SHA-256) + scheduled purge job + `adminPasswordHash` null'lama.
- **Durum:** Açık — provisioning akışına ve migration'a dokunuyor; SMTP/user-lifecycle fazıyla birlikte değerlendirilmeli (ROADMAP).

### RISK-31
**K-21 HTTP test coverage (P1)**
- **Durum:** ÇÖZÜLDÜ. `AuthCompanyControllerTest` (register 202/contract/pattern, suggest-subdomain) + DELETE 401 testleri.

### RISK-32
**`updateStatus` state-machine'siz (P2)**
- **Durum:** ÇÖZÜLDÜ. `CompanyStatus.canTransitionTo` (ACTIVE→{SUSPENDED,TERMINATED}, SUSPENDED→{ACTIVE,TERMINATED}; PROVISIONING/TERMINATED terminal); geçersiz geçiş 400.

### RISK-33
**AuditorAware authenticated yazımlarda (P2)**
- **Durum:** ÇÖZÜLDÜ. SecurityContext userId + `"system"` fallback; RISK-3'ü kapatır.

### RISK-34
**SB4 deprecated starter POM'ları (P2) — AÇIK**
- **Bağlam:** SB4 modularizasyonu ile deprecated: `oauth2-resource-server`→`security-oauth2-resource-server`, `web`→`webmvc`, flyway için `spring-boot-starter-flyway`; ayrıca `HttpMessageConverters` deprecated (SB4).
- **Durum:** Açık — build-risk; ayrı değerlendirilir (bilinçli erteli).

### RISK-35
**Last-admin lockout (P0)**
- **Karar:** `LastAdminGuard` — self-delete koşulsuz 409 (`self_delete_forbidden`); post-mutation ≥1 enabled admin-capable user (admin-closure = `all_permissions` flag rolleri + `t_role_parents` aşağı-BFS). 11 write path'e wired; guard revoke'dan önce (reddedilen işlem Redis hasarı bırakmaz). Side-fix'ler: login `enabled` kontrolü (401 `auth_account_disabled`), disable/delete anında token revoke.
- **Durum:** ÇÖZÜLDÜ (2026-08-15).

### RISK-36
**RbacSeeder startup privilege escalation (P0)**
- **Karar:** Seeder startup'ta ASLA kullanıcı rol ataması yapmaz; Admin yalnızca provisioning sırasında explicit `assignAdminTo(user)` ile verilir. Yan düzeltmeler aynı sette: kilitli hesap refresh'te de bloklu (`isEffectivelyNonLocked`); `revoke` rotasyon zincirini (`rotatedTo`) takip ediyor (Redis + InMemory parite).
- **Durum:** ÇÖZÜLDÜ (2026-08-16 + 2026-08-23 Redis-outage seti). Redis kesinti davranışı bilinçli: rate-limit + blacklist fail-open; `rotate`→temiz 401; session list/revoke→boş/false; yalnız `issue` fail-closed → 503 `service_unavailable`. Bilinçli kalan: revoke zincir yürüyüşü Redis'te atomik değil (çoklu-instance çift-logout yarışı, sınırlı zarar).

---

## Teknik Borç (DEBT-XX)

### DEBT-7
**`hashCode()` bug**
- **Durum:** ÇÖZÜLDÜ. ID-bazlı (`id == null ? identityHashCode : id.hashCode()`). **Konvansyon:** transient (pre-persist) entity'yi `HashSet`/`HashMap` anahtarı yapıp persist sonrası lookup güvenli değil (ID `null→UUID` flip).

### DEBT-10
**Provisioning transaction boundary**
- **Durum:** KISMEN ÇÖZÜLDÜ. `createPendingCompany` tam transactional; `verifyAndProvision`'da `CREATE SCHEMA` PostgreSQL implicit commit → DDL tx dışına kaçar. Recovery idempotency ile (`IF NOT EXISTS`, token `usedAt` guard). Tam transactional DDL PostgreSQL'de mümkün değil.

---

## ID Şeması

- **K-XX:** Mimari karar — stratejik yön.
- **RISK-XX:** Tanımlanmış risk — azaltıcı eylem gerekli.
- **DEBT-XX:** Teknik borç — bilinen eksiklik.

Yeni işler sonraki boş ID'yi alır. ID'ler değişmez. İptal edilen karar durumu "İptal" olarak güncellenir, silinmez.
