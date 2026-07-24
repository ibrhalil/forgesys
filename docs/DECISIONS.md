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
- **Karar:** `t_plans`, `t_subscriptions`, `t_tenant_modules`, `t_module_catalog` yapısı. Tenant signup -> varsayılan FREE + default modüller (Tasks+Notes). Modül aktivasyonu plan kontrolü + Flyway tenant migration + permission seed adımlarından oluşur.
- **Durum:** Planlandı (Faz 3.0.A). Finansal tarafı (gerçek ödeme) Faz 6.
- **Etki:** Modül aktivasyonu çok adımlı (plan doğrula -> şema migrate -> permission seed -> kayıt).

### K-18
**Nginx ertelendi, Faz 2 önceli (2026-07-09)**
- **Bağlam:** Orijinal plan Faz 1.5'te 3-container full separation + Nginx dev'de aktif idi. Kullanıcı Faz 3 öncesi tam RBAC platformu istiyor (user CRUD, yetki atama, login/token, 3 katmanlı log, admin/user frontend).
- **Karar:** Faz 1.5 (Nginx topology) Faz 2 sonrasına ertelendi. Doğrudan Faz 2'ye geçildi. Backend-önceli sıralama: tüm Faz 2 backend bitince Faz 4.0.B frontend gelir.
- **Durum:** Uygulandı (erteleme).
- **Etki:** Vite proxy dev'de Nginx'in görevini görüyor; prod tek app container. Faz 1.5 ticketları (toplamda sayılır) pasif.

### K-19
**3 Katmanlı Log**
- **Bağlam:** Kurumsal bir platform için observability ve denetim gerekiyor. Farklı amaçlar için farklı log türleri.
- **Karar:** Üç ayrı log katmanı, her birinin kendi tablosu + endpoint'i:
  1. **Audit log** — admin aksiyonları (actor/action/entity/old-new JSONB/ip/trace_id). AOP `@AuditLog` annotation ile otomatik.
  2. **Giriş geçmişi (login history)** — user/success/ip/user_agent/reason. Login/refresh/register/logout'ta.
  3. **Request/trace log** — MDC traceId + `X-Request-Id` header. `RequestLoggingFilter` ile.
- **Durum:** Planlandı (Faz 2.10). Request/trace altyapısı Faz 2.0 Foundation'da gelir.
- **Etki:** Yeni tenant migration: `tenant/V2__audit_login_history.sql` (`t_audit_logs` + `t_login_history`).

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

---

## Risk Kayıtları (RISK-XX)

### RISK-3
**AuditorAware hardcoded "system"**
- **Bağlam:** JPA auditing için `AuditorAware` şu an her zaman `"system"` döndürüyor. Kimliği doğrulanmış kullanıcı yok.
- **Karar:** Auth kurulunca SecurityContext'ten gerçek userId alınacak. **Ancak** tenant signup endpoint'leri her zaman `"system"` ile audit edilir (tenant signup context'inde kimliği doğrulanmış kullanıcı yok) — bu beklenen durum.
- **Durum:** Açık. Faz 2 auth sonrası düzeltilecek.

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
- **Durum:** Açık. Refactor Faz B (kullanıcı tarafından ertelendi).

### RISK-21
**`tokenInvalidBefore` kontrol edilmiyor (P1)**
- **Bağlam:** `UserAccount.tokenInvalidBefore` alanı var ama **hiçbir filter/service kontrol etmiyor** (grep doğruladı — sadece entity/Javadoc/docs). Logout sadece cookie expire, JWT geçerli kalır (15 dk). `changePassword`/`resetPassword` token invalidate etmiyor → çalınan token, şifre değiştirilse bile çalışmaya devam eder.
- **Karar:** `JwtAuthenticationFilter`'a `tokenInvalidBefore` kontrolü (issue sonrası Redis cache; ilk dilimde DB lookup) + `changePassword`/`resetPassword`/`logout`'ta `tokenInvalidBefore = now()` set. Epic 2.5/2.6 (Redis blacklist) ile tamamlanır.
- **Durum:** Açık. Refactor Faz A.

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
- **Durum:** Açık. Refactor Faz C (RISK-20 test altyapısından sonra doğrulanmalı).

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
- **Durum:** Açık. Refactor Faz B.

### RISK-32
**`PlatformCompanyService.updateStatus` state-machine'siz (P2)**
- **Bağlam:** Her `CompanyStatus` → her `CompanyStatus` geçişi mümkün (TERMINATED→ACTIVE yeniden canlandırma, ACTIVE→PROVISIONING geri alma, PROVISIONING→ACTIVE schema/admin olmadan — login kırılır).
- **Karar:** `CompanyStatus.canTransitionTo()` veya `EnumSet` allowed-transitions; `updateStatus` doğrula, geçersizse `BUSINESS_ERROR`.
- **Durum:** Açık. Refactor Faz E.

### RISK-33
**AuditorAware hardcoded "system" authenticated yazımlarda (P2)**
- **Bağlam:** RISK-3 aynı sorun, ama authenticated admin işlemlerinde (`UserService`, `RoleService`, `GroupService`, `changePassword`) gerçek `userId` kullanılmıyor. Signup/provisioning için beklenir ama admin CRUD için değil.
- **Karar:** `AuditorAware` `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` → `CustomUserDetails.getUserId()`, fallback `"system"` (signup background). RISK-3'ü kapatır.
- **Durum:** Açık (RISK-3 alt kategorisi). Refactor Faz E.

### RISK-34
**Spring Boot 4 deprecated starter POM'ları (P2)**
- **Bağlam:** SB4 modularizasyonu ile bazı starter'lar deprecated ("will be removed in a future release" — resmi migration guide): `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-web` → `spring-boot-starter-webmvc`. Flyway için sadece `org.flywaydb:flyway-core` yerine `spring-boot-starter-flyway` öneriliyor. Ayrıca `HttpMessageConverters` deprecated (SB4), `@JsonComponent`→`@JacksonComponent` (Jackson 3).
- **Karar:** Deprecated starter'ları yenileriyle değiştir; custom `HttpMessageConverters` bean varsa `ServerHttpMessageConvertersCustomizer`'a geçir.
- **Durum:** Açık. Refactor Faz E.

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
