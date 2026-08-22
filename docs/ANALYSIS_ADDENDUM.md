# ForgeSys — Analiz Eki (Addendum)

> **Tarih:** 2026-08-22 · **Kapsam:** [`FULL_ANALYSIS.md`](FULL_ANALYSIS.md)'yi tamamlayan ek bulgu envanteri (aynı planlama session'ının ikinci keşif turu + çapraz doğrulama sonucu).
> **Durum notu:** Bu session'da kod DEĞİŞMEDİ. Bu doküman envanter + karar kaydıdır; alınan kararların ADR karşılıkları [`DECISIONS.md`](DECISIONS.md)'de **K-37..K-40** olarak yazıldı. Çelişki durumunda DECISIONS.md esas alınır.
> Bu doküman FULL_ANALYSIS.md'nin **yerine geçmez** — onun 5/6/8/12/13/14 bölümlerini genişletir ve iki yerde **override** eder (aşağıda işaretli).

---

## 1. Ölü / spekülatif kod envanteri — Karar D-2 → [K-38: kaldır]

Proje ilkesi: *"ileride lazım olur" düşüncesiyle kod yazılmaz; planlar DECISIONS.md'de yaşar.* Pre-1.0 penceresinde (deploy edilmiş DB/client yok — K-36 varsayımı) aşağıdakiler kaldırılır; git history yeniden ekleme kaynağıdır.

| # | Öğe | Kanıt (grep-verify) | Kaldırınca ne zaman geri gelir |
|---|-----|---------------------|-------------------------------|
| 1 | `OrganizationDomain` entity + `OrganizationDomainRepository` + `t_organization_domains` (public `V1`) | backend main'de **sıfır referans**; `verificationMethod` hiç yazılmıyor (CHECK constraint boşa bekliyor) | Email-domain self-register akışı (Epic 2.9 ertelenmiş) gelirse — yeni `V2` migration ile |
| 2 | `OwnershipGuard` + `Ownable` interface (backend/security + persistence) | main'de kullanım yok (yalnızca kendi test'i çağırıyor); `Ownable`'ın implement eden entity'si yok | İlk ABAC ihtiyacı olan modül (Notes/Warehouse/Logistics) geldiğinde — template'in değeri o an ortaya çıkar |
| 3 | `Company.dbRole` kolonu | Hiç populate/read edilmiyor (yalnızca `@ToString(exclude)`); DB-role-per-tenant izolasyonunun gerçekleşmemiş vestijı | O izolasyon modeli karara bağlanırsa (bugün roadmap'te yok) |
| 4 | `User` token alanları: `emailVerificationToken`/`emailVerificationTokenExpiresAt`/`passwordResetToken`/`passwordResetTokenExpiresAt` | Backend main'de okuma/yazma yok; akış Epic 2.5/2.9'da ertelendi | Tenant-içi email verification + password reset akışı (Faz 5 SMTP ile) geldiğinde |
| 5 | Frontend `usersApi.resendVerification` hook'u | Backend'de endpoint yok → çağrılırsa 404 | Aynı akışla (madde 4) |
| 6 | `t_sessions_log` planı | Tablo hiç yaratılmadı; K-28'de "ertelendi" denmişti | — **İptal** olarak kapatılır (FULL_ANALYSIS §5.3 ile aynı yön) |

> **FULL_ANALYSIS override #1:** §5.7 User token alanları için "Keep + akışı bitir" deniyordu. Session kararı (D-2): **kaldır** — alan beklemek yerine akış kendi migration'ını getirir. Pre-1.0 penceresinde dead column taşımak, ilkenin ihlalidir.

**Not:** `ProjectType.NOTES`, `PropertyType.FORMULA`, tek-değerli `ModuleStatus`/`SubscriptionStatus` enum'ları **kalır** — bunlar enum değeri düzeyinde (kod/kolonyok), akış sahipleri mevcut roadmap'te ve kararları alınmış durumda.

## 2. API tutarsızlık envanteri — Karar D-3 → [K-37: pre-1.0 tek geçiş]

Deploy edilmiş client yok; K-36 squash felsefesiyle aynı pencere. Tek seferlik tutarlılık geçişi:

| # | Tutarsızlık | Mevcut durum | Hedef |
|---|-------------|--------------|-------|
| 1 | Sayfalama bölünmüş | `PageResponse<T>`: users/roles/groups/projects/apps/app-records/audit-logs/login-history. **Unpaged**: `PermissionController.list`, `TaskController.list`, app properties/views listeleri, tüm session listeleri | Standart `PageResponse`; sınırlı pick-list'ler (permissions kataloğu gibi) belgeli istisna olabilir — implementation'da endpoint başına netleşir |
| 2 | Çift `/me` | `GET /auth/me` (claims-only, DB'siz) + `GET /users/me` (DB, tam profil) | Tek `/me` (`/users/me` canonical; `/auth/me` kalkar — pre-1.0'da deprecasyon süreci gerekmez) |
| 3 | `DELETE /users/{id}/lock` → 200 + body | Diğer tüm DELETE'ler 204 | 204 (veya semantik olarak `POST .../unlock` — implementation kararı) |
| 4 | User session namespace 4 controller'a bölünmüş | `SessionController` `/api/v1/users/**` path'leri taşıyor (ad≠path) + `AllSessionsController` `/api/v1/sessions` | Session endpoint'leri tek controller'da toplanır; controller adı = path namespace |
| 5 | `AuditController` class-level `@RequestMapping` yok | Tek istisna (full path'ler inline) | Class-level mapping (konvansiyon) |
| 6 | `AuthController.registerCompany` → `Map<String,Object>` | Dokümante tek istisna | ~~Proper response record~~ — **envanter bulgusu eski kalmış**: kod zaten `CompanyRegisterResponse` record döndürüyordu; kural (Map dönüş yok) AGENTS.md'ye işlendi (K-37 uygulamasında teyit) |

**Springdoc-openapi (Karar D-6):** bu geçiş **bittikten sonra** eklenir — şema stabilken. dev'de açık, prod'da kapalı. (Ask-first: dependency onayı implementation session'ında.)

## 3. Kod tekrarları — Karar D-5 → [K-40: tek temizlik fazı]

| # | Tekrar | Kanıt | Hedef |
|---|--------|-------|-------|
| 1 | Plan çözümleme zinciri iki kez | `PlanLimitService.activePlan` (PlanLimitService.java:64-82) ≡ `ModuleActivationService.activePlanRank` (ModuleActivationService.java:203-208) — aynı TenantContext→Company→ACTIVE Subscription→plan zinciri | Tek kaynak (`PlanLimitService`); ModuleActivationService ona delege |
| 2 | Cookie build/expire kopyası | `AuthController.java:145-154` ≡ `SessionController.java:114-123` birebir; ayrıca `SessionController.REFRESH_COOKIE = "sf_refresh_token"` hardcoded (:49) — config rename'i breakpoint | Tek helper; cookie adı `JwtCookieProperties`'tan |
| 3 | Rate-limiter refill matematiği | Redis Lua + `InMemoryRateLimiter` (bilinçli — Docker'sız test) | **Kalır**; parity test ile korunur (mevcut durum, yeni davranış değil) |

## 4. Startup scalability — Karar D-5 → [K-40]

| Sorun | Kanıt | Hedef |
|-------|-------|-------|
| Startup runner'ları full-entity `companyRepository.findAll()` | `TenantMigrationRunner.java:26` + `ModuleSyncRunner.java:61` + `RbacSeeder.java:64` — her restart'ta tüm Company entity'leri yüklenir; tenant sayısıyla doğrusal | Projection query (id + schemaName + status); entity yüklemesi yok |

## 5. Frontend kalite gate'leri — Karar D-4 → [K-39]

| # | Bulgu | Karar |
|---|-------|-------|
| 1 | `tsconfig.app.json`'de **`strict: true` yok** (yalnızca `noUnused*`/`noFallthrough`/`verbatimModuleSyntax` vb.); `: any` sıfır olduğu için eksiklik maskeleniyor | `strict: true` açılır; `tsc -b` hatasız geçmeli (2026 TS standardının en temel gate'i) |
| 2 | **Sıfır test** (Vitest/RTL "planlı" olarak duruyor); CI'de yalnızca lint+build | Vitest + React Testing Library kurulur; ilk testler: `lib/api.ts` refresh akışı + Login page + kritik primitive'ler (DataTable sort/pagination, Modal focus trap); CI'e `npm test` adımı eklenir |
| 3 | Liste-sayfa scaffold'ı **7 sayfada kopya** (state + `useDebouncedValue` + `handleSort` + page-reset `useEffect` + `DEFAULT_PAGE_SIZE`, ~40 satır/yer: Users/Roles/Groups/Permissions/Projects/AuditLogs/LoginHistory) | `useListPageState` hook — tek abstraction, gerçek (7×) tekrar; rule-of-three çoktan aşılmış |
| 4 | `lib/i18n/messages.ts` 864 satır | **Kalır** — dictionary (data), kod değil |

## 6. Test-parite notu

- Test profilinde `ModuleProperties` yok → fallback `default-keys = pm` (dev/prod: `pm,apps`). **Bilinçli** (H2'de modül migration'ları örtük koşmasın; IT'ler modülleri explicit aktive eder) — AGENTS.md'lerde kayıtlı, burada çapraz referans için not düşüldü.
- H2 blind spot: tek `public` şema → `SET search_path` izolasyon mekanizması H2 suite'inde **hiç koşmaz**; gerçek PG doğrulaması gated IT'lerin (`CrossTenantIsolationTest`, `ModuleActivationIT`, `AppBuilderIT`) işi. (Bilinen durum — FULL_ANALYSIS §2.11 ile uyumlu.)

## 7. Doküman drift — bu session'da düzeltülenler

| Dosya | Drift | Düzeltme |
|-------|-------|----------|
| `docs/ARCHITECTURE.md` | Giriş "mevcut Faz 1'i belgeler; auth/RBAC/modüller planlandı" (stale); Redis diyagramda kesikli "Faz 2.6" (aktif kullanımda); şema→tablo haritası `t_plans`/`t_subscriptions`/`t_tenant_modules` ve `module/apps` ailesini içermiyor; entity diyagramı Plan/Subscription/TenantModule/App*/AuditLog/LoginHistory/Project/Task yok | ✅ Düzeltildi |
| `docs/DECISIONS.md` | K-20 "Planlandı" (DONE 2026-08), K-28 "PLANLANDI" (DONE 2026-07-30); K-1..K-14 ve DEBT-1..6/8/9 kayıtları hiç back-fill edilmemiş | ✅ Durumlar düzeltildi + kayıt-boşluğu notu eklendi; K-37..K-40 yazıldı |
| `persistence/AGENTS.md` | `UserRepository.findByRolesEmpty` listeleniyor — metot RISK-36 fix'inde silindi | ✅ Referans kaldırıldı |
| `infra/README.md` | "Redis data named volume'da, `infra/data/` altında değil" — yalnız dev doğru; prod bind-mount `./infra/data/redis` | ✅ Dev/prod ayrımı netleştirildi |
| `README.md` | Env tablosunda `BASE_DOMAIN` (kodda yok, `.env.example`'te yok, compose geçirmiyor); `PASSWORD_PEPPER` `.env.example`'te var ama compose `environment:` listesinde **yok** (yalnız `infra/config` overlay ile konteynere ulaşır — belirtilmemiş); "Planlanan" bölümünde Custom App Builder + modül sistemi (backend'i shipped) | ✅ Düzeltildi |

## 8. Yeni standart kararlar (FULL_ANALYSIS §11'e ek — tekrar tartışılmaz)

| # | Karar | ADR |
|---|-------|-----|
| 21 | Speculative kod eklenmez; ölü kalan kaldırılır (planlar DECISIONS.md'de yaşar, kodda değil) | K-38 |
| 22 | API konvansiyon: `PageResponse` standart (belgeli pick-list istisnaları), tek `/me`, DELETE→204, controller adı = path namespace, DTO record (`Map` dönüş yok) | K-37 |
| 23 | Frontend: strict TS + yeni feature'da test zorunlu; liste-sayfa scaffold'u `useListPageState` üzerinden | K-39 |
| 24 | Startup runner'ları entity değil projection yükler; paylaşılan çözümleme zincirleri tek kaynakta yaşar | K-40 |

---

## Uygulama sırası (hatırlatma)

```
Session 0 (bu session — dokümantasyon) ✅
Session 1 — Backend temizlik (K-38 kaldırma + K-37 API geçişi + K-40 tekrar/scalability)
  ✅ K-38 uygulandı (2026-08-22): baseline V1 düzenlemesi + tüm ölü küme kaldırıldı
    (462 H2 + 9 gated PG IT yeşil; local DB reset gerektirir).
  ✅ K-37 uygulandı (2026-08-22): permissions/tasks PageResponse, tek /users/me,
    POST /unlock 204, session controller rename'leri (ad=path), AuditController
    class-mapping; registerCompany zaten DTO imiş (envanter notu düzeltildi).
    (469 H2 + 9 gated PG IT yeşil; frontend göçü dahil.)
  ✅ K-40 uygulandı (2026-08-22, Session 3): startup runner projection
    (`CompanyRepository.TenantSchemaView`), tek plan çözümleme zinciri
    (`PlanLimitService.tryActivePlan`), cookie helper tek noktada
    (`JwtCookieProperties`). 462 H2 + 9 gated PG IT yeşil; refactor, davranış değişikliği yok.
Session 2 — Frontend kalite (K-39)
  ✅ K-39 uygulandı (2026-08-22): strict TS (0 hata ile geçti), Vitest+RTL
    altyapısı (Node 20 pin'i: jest-dom 6 / jsdom 29) + 20 test (api refresh,
    LoginPage, DataTable, Modal, useListPageState), 7 sayfa list-scaffold göçü
    (Permissions kısmi — client pagination), CI npm test adımı.
Session 3 — Springdoc-openapi (D-6 — K-37 sonrası)
  ✅ K-41 uygulandı (2026-08-22, Session 5): springdoc 3.1.0 (SB4/Jackson 3
    hattı), dev'de açık / prod'da kapalı, cookieAuth scheme dokümante,
    NoResourceFoundException → 404 side-fix. 466 H2 test + dev runtime kontrolü yeşil.
```

> FULL_ANALYSIS §14'deki "Critical" bloğu bu session'ın kararlarıyla şu şekilde güncellenir: frontend test altyapısı (K-39) ve API tutarlılık geçişi (K-37) aynı önceliktedir; springdoc (D-6) bilinçli olarak onların ARKASINA alınmıştır.
