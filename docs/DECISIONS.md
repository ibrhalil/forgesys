# Karar Kayıtları (Decision Log)

> Bu dosya SystemForge'un mimari/teknik kararlarının (K-XX), risk kayıtlarının (RISK-XX) ve teknik borçlarının (DEBT-XX) tek merkezi. ID'ler karar verildiği sırayla artar, değişmez. Kararlar ticket numarasına değil, bağlam+gerekçe+etki'ye bağlıdır.

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
**Hibrit Tenant Signup Verification (2026-07-20) — PLANLANDI, UYGULANMADI**
- **Bağlam:** Mevcut `provisionTenant` open endpoint + ağır DDL (schema CREATE + Flyway) + subdomain/emailDomain squatting'e karşı korumasız. RBAC/auth kurulmadan önce signup yolunu sağlamlaştırmak gerekiyor.
- **Karar:** İki fazlı hibrit akış:
  1. `POST /api/v1/auth/company/register` — `PROVISIONING` Company + `TenantVerificationToken` yaratır (şema/migration YOK, hafif). `VerificationSender` ile doğrulama linki gönderir.
  2. `POST /api/v1/auth/company/verify` — token consumes -> SENKRON schema CREATE + Flyway tenant migration + admin user -> Company `ACTIVE`, token `usedAt`.
  Tetikleyici polling/event DEĞİL, kullanıcının linke tıklaması (HTTP request).
- **Durum:** PLANLANDI — koda uygulanmadı. Mevcut kod tek-fazlı senkron `provisionTenant` (direkt ACTIVE). Bu karar ROADMAP'te Epic 2.0.C olarak bekliyor.
- **Etki (planlandığında):**
  - `TenantVerificationToken` entity (`public` şema, `GeneratedIdAuditEntity` — soft-delete'siz, `usedAt` ile invalidasyon)
  - `public/V2__tenant_verification_tokens.sql` migration
  - `VerificationSender` interface + profile bazlı impl'ler: `test`->`InMemoryVerificationSender`, `dev`->`LogVerificationSender`, `prod`->`MailVerificationSender` (mail starter SF sonrası erteli)
  - `CompanyStatus.PROVISIONING` gerçekten kullanılır hale gelir
  - Tenant signup admin email doğrulaması (`public` şema) ile tenant içi user email doğrulaması (`User.emailVerificationToken`, tenant şeması) AYNI şey DEĞİL.
- **Not 1 (migration çakışması):** `public/V2` ve `tenant/V2` sürümleri RISK-17 partial index işiyle dolu (bkz. Epic 2.0.B). K-21 implement edilirken tenant verification token tablosu `public/V3` olmalı (V2 ÇÖZÜLDÜ).
- **Not 2:** Backend/persistence AGENTS.md'leri şu an bu kararı "mevcut" gibi anlatmıyor; gerçek tek-fazlı akışı belgeliyor. K-21 uygulandığında bu dokümanlar güncellenir.

### K-22
**Tenant Domain Handoff / Schema Archival (2026-07-22) — PLANLANDI**
- **Bağlam:** Nadir ama gerçek bir senaryo: bir şirket aboneliğini kapatır/ödemez → `SUSPENDED`/`TERMINATED`. Daha sonra aynı email domain'i (örn. şirket iflası, domain başkasına geçti) farklı bir kişi tarafından tekrar SystemForge'a kayıt olmak isteyebilir. Eski şirketin subdomain/emailDomain/schema_name değerleri serbest kalmalı, ama eski veri kaybolmamalı (arşiv).
- **Karar:** İki katmanlı yaklaşım:
  1. **Kısıt katmanı (RISK-17 partial index ile sağlandı):** Soft-delete edilmiş şirketin `subdomain`/`email_domain`/`schema_name` değerleri `WHERE is_deleted = false` partial index sayesinde aktif satırlar arasında benzersiz kalmaya devam ederken, silinen satır tekrar kullanılabilir. Yeni kayıt temiz geçer.
  2. **Fiziksel arşiv (operasyonel, platform admin):** Eski şirketin fiziksel şeması `ALTER SCHEMA tenant_X RENAME TO tenant_X_archived` ile yeniden adlandırılır. Yeni kayıt fresh `tenant_X` şeması + Flyway ile yaratılır; eski veri `tenant_X_archived`'da platformdan ayrı (orphan) kalır.
- **Durum:** PLANLANDI. Katman 1 (partial index) uygulandı (RISK-17, Epic 2.0.B). Katman 2 (fiziksel schema rename + reaktivasyon onay maili) platform admin tooling / Faz 6 (Billing & Abonelik) kapsamında gelecek.
- **Etki:** RISK-17 kapsamı açıkça `t_companies` dahil 9 index olacak şekilde tasarlandı (sadece tenant-side değil) — bu senaryoyu destekler. `CompanyStatus` yaşam döngüsü (ACTIVE → SUSPENDED → TERMINATED + reaktivasyon) Faz 6 ile netleşecek.

### K-23
**Global Password Pepper (HMAC pre-hash + BCrypt)**
- **Bağlam:** Şifre hash'leri DB'de BCrypt(12) olarak saklanıyor (RISK-13). BCrypt salt'ı hash'e gömüyor (per-user, standart) ama DB leak senaryosunda saldırgan hash tablosunu alıp offline brute-force yapabilir — pepper (DB dışında tutulan global secret) olmadan tek başına BCrypt yetersiz. Per-tenant pepper değerlendirildi: DB leak tehdit modeli için **ek güvenlik sağlamaz** (saldırgan DB'yi okuyor, pepper DB'de değilse zaten göremiyor — per-tenant'ın ek katkısı ancak "bir tenant'ın pepper'ı bağımsız sızarsa" senaryosunda, ki bu başka tehdit). Per-tenant pepper N key yönetimi + pepper kaybında o tenant'ın tüm şifreleri kurtarılamaz riskini getirir.
- **Karar:** **Global** pepper, BCrypt'in native pepper desteği olmadığı için **HMAC-SHA256 pre-hash** stratejisiyle (OWASP önerisi): raw şifre önce `HMAC-SHA256(pepper, password)` → Base64 (32 byte → 44 char, BCrypt 72-byte limit altında), sonra BCrypt(12). `PepperingPasswordEncoder` BCrypt'i wrap'lar. Pepper'lı hash'ler `{sf-peppered}` marker prefix'i ile işaretlenir; legacy pepper'sız BCrypt hash'ler (`$2a$12$...`) hâlâ `matches()` ile geçerli ve ilk başarılı login'de pepper'lı formata **lazy rehash** edilir (RISK-13 felsefesiyle aynı). Pepper `systemforge.security.password-pepper` / `PASSWORD_PEPPER` env var'ından; **boşsa startup fail-fast** (SecurityConfig). test/dev profilleri non-secret default sağlar; prod mutlaka gerçek secret sağlamalı.
- **Durum:** Uygulandı.
- **Etki:**
  - **DB leak tek başına artık hash kırımı için yetersiz** — pepper DB dışında (env/secret manager/config overlay).
  - **Pepper rotasyonu ŞU AN DESTEKLENMİYOR:** pepper değişirse tüm pepper'lı hash'ler geçersiz olur (legacy hash'ler hâlâ doğrulanır ama pepper'lı olanlar değil). Rotasyon gerektiğinde özel migration akışı tasarlanmalı (tüm kullanıcılar şifre sıfırlama).
  - Pepper'ı asla logla/commit etme (AGENTS.md "Never log sensitive data" kuralı).
  - **Per-tenant pepper** yalnızca BYOK/regülasyon (KVKK/HIPAA) gerektiğinde tekrar değerlendirilmeli — ve o zaman JWT signing key'leri de per-tenant yapılmalı (tutarlı crypto isolation).
  - `AuthService.login` artık read-write transaction (rehash write edebilir); pepper'lı user'larda no-op.
  - Mevcut `DelegatingPasswordEncoder`'a geçiş ertelendi (over-engineering şu an); algoritma değişimi (Argon2id) istenirse marker+wrapper yapısı taşınabilir.

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

---

## Teknik Borç (DEBT-XX)

### DEBT-7
**hashCode() bug (ÇÖZÜLDÜ)**
- **Bağlam:** `BaseEntity` ve `GeneratedIdAuditEntity` `Objects.hash(getClass())` kullanıyor -> aynı tipteki tüm entity'lere aynı hash -> `Set<Role>` gibi koleksiyonlarda çakışma.
- **Karar:** `hashCode()` ID baz alınacak şekilde düzeltilecek (her iki base sınıfta).
- **Durum:** ÇÖZÜLDÜ (Epic 2.0.B). Uygulama: `id == null ? System.identityHashCode(this) : id.hashCode()`. Persist öncesi (id null) identity hash, persist sonrası ID-bazlı. RBAC'dan önce düzeltildi.
- **Konvansiyon:** ID persist sırasında `null→UUID` değiştiği için yeni (transient) entity'yi persist **öncesinde** `HashSet`/`HashMap` anahtarı olarak kullanıp persist sonrası lookup yapmak güvenli değildir. Pratik kullanımda (DB'den yüklenen entity'ler) sorun yok.

### DEBT-10
**provisionTenant transaction'suz**
- **Bağlam:** `TenantProvisioningService.provisionTenant()` ve `createAdminUser()` `@Transactional` değil. Kısmi write riski (DDL başarılı, JPA write fail -> yarım tenant).
- **Karar:** Yazma işleri `@Transactional` (method-level) olacak; lookup'larda `readOnly=true`. K-21 uygulanırken refactor edilir (`createPendingCompany` + `verifyAndProvision` her ikisi de transactional).
- **Durum:** Açık. K-21 (Epic 2.0.C) ile birlikte çözülür.

---

## ID Şeması

- **K-XX:** Mimari karar (Architecture decision). Stratejik yön.
- **RISK-XX:** Tanımlanmış risk — azaltıcı eylem gerekli (mitigation).
- **DEBT-XX:** Teknik borç — bilinen eksiklik, refactor bekliyor.

Yeni keşfedilen işler sonraki boş ID'yi alır. ID'ler değişmez. İptal edilen karar durumu "İptal" olarak güncellenir, silinmez (geçmiş iz için).
