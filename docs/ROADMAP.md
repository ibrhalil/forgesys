# Yol Haritası (Roadmap)

> **Kalan işler** planı. Tamamlanan epiklerin özeti aşağıdaki tabloda; karar/kayıt detayları [DECISIONS.md](DECISIONS.md)'de, güncel sistem [ARCHITECTURE.md](ARCHITECTURE.md)'de. Ticket numarası tablosu YOK — geliştirici kendi `SF-NN` tag'ini verir (branch/commit), bu dosyaya bağımlı değil.

## Mevcut Durum

Platform çekirdeği kullanımda: schema-per-tenant multi-tenancy, iki fazlı tenant signup, auth (RS256 JWT cookie + Redis refresh rotasyon/reuse detection), tam RBAC (rol kalıtımı + `all_permissions` + last-admin guard + privilege-change session revoke), audit/login/request log (append-only + `@AuditLog` AOP + delta), modül & plan sistemi (registry kodda), üç modül aktif ve **tipli proje konteynerine** çapalı (**pm** TASKS, **apps** APPS koleksiyonu, **notes** NOTES — K-45), admin console (permission-gated), Prometheus metrics (prod ayrı management portu), CI (backend + frontend + gated gerçek PG/Redis IT) + GHCR publish (deploy manuel).

## Tamamlanan Epikler (özet)

| Alan | Kapsam | Kayıt |
|---|---|---|
| Faz 1-2 — altyapı + auth/RBAC | multi-tenancy + Flyway, security, JWT, RBAC CRUD, platform namespace, iki fazlı signup, self-service | K-21..K-26 |
| IAM hardening | privilege-change revoke, max-sessions, append-only audit + delta, app-level rate limit, rol kalıtımı, `all_permissions`, last-admin guard, user directory read model + scoped görünürlük, RbacSeeder escalation fix | RISK-35, RISK-36 |
| Faz 3.0 — module system + app builder backend | plan/modül registry + aktivasyon, JSONB EAV, plan limitleri, structured view DSL | K-15, K-16 |
| pm modülü | project-scoped tasks + Kanban board UI | — |
| notes modülü | markdown notes + kategoriler (default-aktif; K-45 ile proje-scoped) | K-44 (+K-45) |
| **K-45 — tipli proje konteyneri (Faz 1)** | `Project` typed container (TASKS/NOTES/APPS; katalog aktif modüllerden), notes/apps proje-scoping migration'ları + nested API'ler, tip-değişim/döngü/default guard'ları, "Genel" default konteynerler, üç yönlü proje detay UI'ı | K-45 |
| Faz 4 — frontend | admin console + App Builder UI (TABLE/BOARD/CALENDAR/LIST/GALLERY renderer'ları, filtre/sort DSL, picker'lar, plan göstergeleri) | K-20, K-42 |
| Kalite/sadeleştirme seti | ölü kod kaldırma, API tutarlılık geçişi, migration squash, strict TS + Vitest/RTL, startup projection, springdoc-openapi | K-36..K-41 |
| Observability + CI/CD | Prometheus expose, CI 3-job + gated IT'ler + GHCR publish | K-43 |
| Audit genişletme | `@AuditLog` AOP, delta kaydı, `t_request_logs` + endpoint + UI, high-risk body masking | K-19, K-27 |

## Kalan İşler

### Kullanıcı lifecycle + mail (SMTP ön koşulu)
- [ ] SMTP altyapısı: `spring-boot-starter-mail` + `MailVerificationSender` (prod; dev'de `LogVerificationSender` kalır) + template'ler (`infra/templates/`, TR/EN)
- [ ] Tenant içi email doğrulama akışı (kendi migration'ını getirir — K-38)
- [ ] Password reset akışı (`forgot-password`/`reset-password`; rate-limit kapsamına alınır)
- [ ] RISK-30 birlikte: verification token hash-at-rest + purge job + `adminPasswordHash` null'lama

### Faz 3 kalanı — built-in modüller
- [ ] **Warehouse:** ürün/depo/stok kalemi/stok hareketi (IN/OUT/TRANSFER) + minimum stok uyarısı
- [ ] **Logistics:** sevkiyat/araç/sürücü/route + sevkiyat durum makinesi (CREATED→IN_TRANSIT→DELIVERED)

### Faz 5 kalanı — hardening & operasyon
- [ ] K-29 notification subsystem (in-app kanalı bağımsız yapılabilir; mail SMTP'ye bağlı)
- [ ] K-30 activity feed (audit log üstünden türetme + i18n template map)
- [ ] K-27 artıkları (LOW): approval workflow (`t_pending_actions`), anomaly detection
- [ ] E2E Playwright — critical path'ler (signup→verify→login, modül CRUD, password reset)
- [ ] OpenTelemetry tracing (K-33 gateway ile birlikte değerlendirilecek)
- [ ] Nginx gateway + wildcard TLS (K-33 — proje %90 sonrası; eski Faz 1.5 epikleri bu kapsamda uygulanır)
- [ ] Pepper rotasyon runbook (docs)
- [ ] Deploy otomasyonu (GHCR publish var; sunucuya rollout manuel)

### K-45 sonraki artışlar (taahhütsüz yön — K-45'te tanımlı)
- [ ] Faz 2 — proje görünüm sekmeleri (AppView'in DSL konsepti Task/Note listelerine genişletilir; tablo soyutlaması genelleştirilmez)
- [ ] Faz 3 (talep-kapılı) — `t_links` polymorphic bağlantı katmanı (dondurulmuş anti-şişme kuralları K-45'te)

### Faz 6 — Billing (K-16 finansal taraf)
- [ ] Ödeme sağlayıcı spike (Stripe vs iyzico — Türkiye pazarı) + entegrasyon + webhook
- [ ] Plan upgrade/downgrade akışı (soft-block limit yönetimi) + invoice + 14 gün PRO trial
- [ ] Platform admin dashboard (MRR, churn, tenant istatistikleri) + tenant lifecycle (K-22 arşiv katmanı)

### Ürün kararları bekliyor
- [ ] Password complexity policy (tüm test/bootstrap şifrelerini değiştirir)
- [ ] Tenant içi email verification zorunlu mu opsiyonel mi (akış yapılırken netleşecek)

### Bilinçli ertelenmiş / iptal (tekrar tartışılmaz)
MapStruct (iptal — manuel `toResponse`) · `t_sessions_log` (iptal — K-28) · `PermissionCacheService` (düşük değer — yetkiler JWT'de gömülü) · `PasswordEncodingListener` · TaskDecorator (RISK-10 — ilk `@Async` tüketiciyle) · OAuth2 sosyal giriş / WebSocket-SSE / S3-MinIO / LDAP-SSO / microservice (Faz 5 değerlendirme listesi) · FORMULA property tipi (K-15) · drag-drop + expression editor (K-42) · ABAC görünürlük + WYSIWYG + tsvector search (K-44) · OTel (K-43 notu)

## İlgili Dokümanlar

- [Karar kayıtları](DECISIONS.md) — K-XX/RISK-XX/DEBT-XX + dondurulmuş kararlar
- [Mimari](ARCHITECTURE.md) — bileşen diyagramı, request lifecycle, schema-per-tenant, config profilleri
- [README](../README.md) — kurulum, çalıştırma, API
- [AGENTS.md](../AGENTS.md) (kök + modül bazlı) — güncel kurallar
