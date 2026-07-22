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

### K-15 — Custom App Builder (Notion-style)
- **Bağlam:** Tenant'ların kendi ihtiyaçlarına özel mini-uygulamalar yaratması (Notion/Airtable mantığı) gerekiyor. Sadece sabit built-in modüllere değil, esnek veri modeline ihtiyaç var.
- **Karar:** JSONB EAV modeliyle tenant custom app'leri desteklenir. `t_apps`, `t_app_properties`, `t_app_records`, `t_app_record_values(value JSONB)`, `t_app_views`. Property tipleri: TEXT/NUMBER/SELECT/DATE/USER/RELATION/FORMULA.
- **Durum:** Planlandı (Faz 3.0.B).
- **Etki:** Hibrit ürün modeli: built-in modüller (Odoo/ERPNext mantığı) + tenant custom app'leri (Notion/Airtable mantığı).

### K-16 — Plan Bazlı Modül Aktivasyonu
- **Bağlam:** Tüm tenant'lar tüm modülleri kullanmamalı. Free/Pro/Enterprise planları modül erişimini belirlemeli.
- **Karar:** `t_plans`, `t_subscriptions`, `t_tenant_modules`, `t_module_catalog` yapısı. Tenant signup -> varsayılan FREE + default modüller (Tasks+Notes). Modül aktivasyonu plan kontrolü + Flyway tenant migration + permission seed adımlarından oluşur.
- **Durum:** Planlandı (Faz 3.0.A). Finansal tarafı (gerçek ödeme) Faz 6.
- **Etki:** Modül aktivasyonu çok adımlı (plan doğrula -> şema migrate -> permission seed -> kayıt).

### K-18 — Nginx ertelendi, Faz 2 önceli (2026-07-09)
- **Bağlam:** Orijinal plan Faz 1.5'te 3-container full separation + Nginx dev'de aktif idi. Kullanıcı Faz 3 öncesi tam RBAC platformu istiyor (user CRUD, yetki atama, login/token, 3 katmanlı log, admin/user frontend).
- **Karar:** Faz 1.5 (Nginx topology) Faz 2 sonrasına ertelendi. Doğrudan Faz 2'ye geçildi. Backend-önceli sıralama: tüm Faz 2 backend bitince Faz 4.0.B frontend gelir.
- **Durum:** Uygulandı (erteleme).
- **Etki:** Vite proxy dev'de Nginx'in görevini görüyor; prod tek app container. Faz 1.5 ticketları (toplamda sayılır) pasif.

### K-19 — 3 Katmanlı Log
- **Bağlam:** Kurumsal bir platform için observability ve denetim gerekiyor. Farklı amaçlar için farklı log türleri.
- **Karar:** Üç ayrı log katmanı, her birinin kendi tablosu + endpoint'i:
  1. **Audit log** — admin aksiyonları (actor/action/entity/old-new JSONB/ip/trace_id). AOP `@AuditLog` annotation ile otomatik.
  2. **Giriş geçmişi (login history)** — user/success/ip/user_agent/reason. Login/refresh/register/logout'ta.
  3. **Request/trace log** — MDC traceId + `X-Request-Id` header. `RequestLoggingFilter` ile.
- **Durum:** Planlandı (Faz 2.10). Request/trace altyapısı Faz 2.0 Foundation'da gelir.
- **Etki:** Yeni tenant migration: `tenant/V2__audit_login_history.sql` (`t_audit_logs` + `t_login_history`).

### K-20 — Admin/User/Log UI Faz 3 öncesi
- **Bağlam:** K-18 sonrası backend Faz 2 tamamlanınca frontend geliyor. Built-in modül UI'ları (Tasks/Notes) beklenebilir ama admin/user yönetimi kritik.
- **Karar:** Epic 4.0.B (Admin/User/Log Management UI) Faz 3 öncesi gelir. Faz 4 core stack (bağımlılıklar, Tailwind, auth UI, router) burada kurulur. Tenant-scoped: her şirket kendi verisini görür.
- **Durum:** Planlandı (Faz 4.0.B, backend Faz 2 sonrası).
- **Etki:** Built-in modül UI'ları hâlâ Epic 4.1'de.

### K-21 — Hibrit Tenant Signup Verification (2026-07-20) — PLANLANDI, UYGULANMADI
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
- **Not:** Backend/persistence AGENTS.md'leri şu an bu kararı "mevcut" gibi anlatmıyor; gerçek tek-fazlı akışı belgeliyor. K-21 uygulandığında bu dokümanlar güncellenir.

---

## Risk Kayıtları (RISK-XX)

### RISK-3 — AuditorAware hardcoded "system"
- **Bağlam:** JPA auditing için `AuditorAware` şu an her zaman `"system"` döndürüyor. Kimliği doğrulanmış kullanıcı yok.
- **Karar:** Auth kurulunca SecurityContext'ten gerçek userId alınacak. **Ancak** tenant signup endpoint'leri her zaman `"system"` ile audit edilir (tenant signup context'inde kimliği doğrulanmış kullanıcı yok) — bu beklenen durum.
- **Durum:** Açık. Faz 2 auth sonrası düzeltilecek.

### RISK-10 — @Async thread'lerde TenantContext taşınmaz
- **Bağlam:** `TenantContext` ThreadLocal. Spring `@Async` yeni thread pool'da çalışır -> context kaybolur.
- **Karar:** `TaskDecorator` ile TenantContext + SecurityContext propagation. Audit/email async işler için gerekli.
- **Durum:** Planlandı (Faz 2.0.B).

### RISK-13 — BCrypt strength
- **Bağlam:** Mevcut `BCryptPasswordEncoder()` default strength 10. Güvenlik standardı 12.
- **Karar:** Faz 2.3'te `BCryptPasswordEncoder(12)`'ye geçilecek. Migration stratejisi (mevcut hash'ler) önce spike ile netleştirilecek.
- **Durum:** Açık. Faz 2.3.

### RISK-14 — oauth2-resource-server jwt filter aktif edilmez
- **Bağlam:** `spring-boot-starter-oauth2-resource-server` (Nimbus) RSA asimetrik imzalama (RS256) için seçildi (jjwt yerine). Ama auto-config filter `tokenInvalidBefore` check yapamaz.
- **Karar:** `.oauth2ResourceServer().jwt()` auto-config filter **AKTİF EDİLMEZ**. Custom `JwtAuthenticationFilter` yazılır (cookie->decode->Redis blacklist check->DB `tokenInvalidBefore`->SecurityContext).
- **Durum:** Planlandı (Faz 2.4).

### RISK-15 — DateTimeProvider bug (ÇÖZÜLDÜ)
- **Bağlam:** `@CreatedDate` OffsetDateTime populate edilmiyordu — Hibernate varsayılan DateTimeProvider UTC timezone'u doğru set etmiyordu.
- **Karar:** Özel `DateTimeProvider` bean (UTC) tanımlandı, `MultiTenancyJpaConfig`'te.
- **Durum:** ÇÖZÜLDÜ.

### RISK-16 — Yeni tenant migration mevcut tenant'larda çalışmaz
- **Bağlam:** Programmatik tenant Flyway migration sadece yeni provision edilen tenant'larda çalışır. Mevcut tenant'lar V1'de takılır; yeni V2 tenant migration'ı onlara uygulanmaz.
- **Karar:** `TenantMigrationRunner` (`ApplicationRunner`) — startup'ta tüm `t_companies` şemalarını Flyway migrate eder. Yeni tenant migration'ından ÖNCE gelmeli.
- **Durum:** Planlandı (Faz 2.0.B).

### RISK-17 — Soft-delete + UNIQUE çakışması
- **Bağlam:** DB seviyesi UNIQUE constraint soft-delete ile çakışır (silinmiş satır kalır, aynı isimde yenisi insert edilemez).
- **Karar:** Partial index: `CREATE UNIQUE INDEX ... WHERE is_deleted = false`. Sadece `SoftDeleteAuditEntity` subclass'ları için. `GeneratedIdAuditEntity` subclass'ları (soft-delete'siz) normal UNIQUE kullanır.
- **Durum:** Planlandı (Faz 2.0.B, User CRUD'dan önce).

---

## Teknik Borç (DEBT-XX)

### DEBT-7 — hashCode() bug
- **Bağlam:** `BaseEntity` ve `GeneratedIdAuditEntity` `Objects.hash(getClass())` kullanıyor -> aynı tipteki tüm entity'lere aynı hash -> `Set<Role>` gibi koleksiyonlarda çakışma.
- **Karar:** `hashCode()` ID baz alınacak şekilde düzeltilecek (her iki base sınıfta).
- **Durum:** Açık. RBAC (Set<Permission> vb.)'dan önce düzeltilmeli (Faz 2.0.B).

### DEBT-10 — provisionTenant transaction'suz
- **Bağlam:** `TenantProvisioningService.provisionTenant()` ve `createAdminUser()` `@Transactional` değil. Kısmi write riski (DDL başarılı, JPA write fail -> yarım tenant).
- **Karar:** Yazma işleri `@Transactional` (method-level) olacak; lookup'larda `readOnly=true`. K-21 uygulanırken refactor edilir (`createPendingCompany` + `verifyAndProvision` her ikisi de transactional).
- **Durum:** Açık. K-21 (Epic 2.0.C) ile birlikte çözülür.

---

## ID Şeması

- **K-XX:** Mimari karar (Architecture decision). Stratejik yön.
- **RISK-XX:** Tanımlanmış risk — azaltıcı eylem gerekli (mitigation).
- **DEBT-XX:** Teknik borç — bilinen eksiklik, refactor bekliyor.

Yeni keşfedilen işler sonraki boş ID'yi alır. ID'ler değişmez. İptal edilen karar durumu "İptal" olarak güncellenir, silinmez (geçmiş iz için).
