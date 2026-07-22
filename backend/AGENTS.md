# backend/AGENTS.md

## Modül

Spring Boot uygulaması — controller/service/security/config. `common` + `persistence`'a bağımlı. Sadece bu modül executable jar üretir (`systemforge-backend.jar`). Kök AGENTS.md'deki genel kurallar geçerli.

Komutlar için bkz. [README](../README.md#build-komutlari). Backend özet: `./mvnw -pl backend spring-boot:run` (root'tan DEĞİL), `./mvnw -pl backend test -Dtest=ClassName#method`.

## Paket Yapısı

Kök paket `com.ibrhalil.systemforge` (`.backend` subpackage DEĞİL):
- `SystemforgeApplication` — main.
- `tenant/` — `TenantFilter` (subdomain çözümleme).
- `controller/` — REST (`/api/v1/*`). `[FAZ 3]` `modules/` alt paketi.
- `service/` — iş mantığı. `[FAZ 3]` `modules/` alt paketi.
- `dto/` — request/response DTO'ları (`record`).
- `exception/` — `GlobalExceptionHandler`, `ErrorResponse`.
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig`.

## Tenant Context Kuralları (KRİTİK)

- `TenantFilter` (`OncePerRequestFilter`): Host header -> subdomain -> `CompanyRepository.findBySubdomain` -> `schemaName` -> `TenantContext`. Sadece `ACTIVE` durumdaki Company'ler çözümlenir (`PROVISIONING`/`SUSPENDED`/`TERMINATED` çözümlenmez).
- `shouldNotFilter()` ile `/api/v1/auth/**` ve `/actuator/**` muaf (tenant bağlamı gerektirmez). `/api/v1/auth/company/register` muaf olmak ZORUNDA — tenant henüz yaratılıyor.
- **Controller'da tenant doğrulama YAPMA** — filter tek sorumluluk alanı.
- Tenant context null ise resolver `"public"` döner -> public şema verilerine (Company) erişilebilir.
- Programmatik tenant set eden servisler (örn. `TenantProvisioningService.provisionTenant` -> `createAdminUser`) `finally`'de `TenantContext.clear()` yapmalı.
- `X-Tenant-ID` header fallback **yalnızca `dev` profilinde** aktif. Prod'da tamamen kapalı.
- `@Async` thread'lerinde `TenantContext` otomatik taşınmaz — `TaskDecorator` gerekir ([RISK-10](../docs/DECISIONS.md#risk-10--async-threadlerde-tenantcontext-tasinmaz)).

## Endpoint Kuralları

- Tüm endpoint'ler `/api/v1/*` prefix'i altında.
- Response DTO (`record`) olarak dönmeli, entity'ler direkt expose EDİLMEZ. **Mevcut istisna:** `AuthController.registerCompany()` `ResponseEntity<Map<String,Object>>` dönüyor — MapStruct + DTO refactor'ı Epic 2.1'de ([ROADMAP](../docs/ROADMAP.md#epic-21--mapstruct--dto)).
- Hata yanıtları tek tip `ErrorResponse` (`GlobalExceptionHandler`): `TenantNotFoundException`/`IllegalArgumentException`/validation -> 400, generic -> 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

### Mevcut Endpoint

| Method | Path | Açıklama | Auth |
|--------|------|----------|------|
| `POST` | `/api/v1/auth/company/register` | Yeni tenant signup — SENKRON: `provisionTenant` `ACTIVE` Company + schema CREATE + Flyway tenant migration + admin user yaratır. | Public |

> **İki fazlı akış planlandı (K-21)** ama uygulanmadı: `PROVISIONING` Company yaratıp email verify ile `ACTIVE`'e çekme. Detay [DECISIONS.md K-21](../docs/DECISIONS.md#k-21--hibrit-tenant-signup-verification-2026-07-20--planlandi-uygulanmadi). ROADMAP'te Epic 2.0.C olarak bekliyor; uygulandığında bu bölüm güncellenir.

> `TenantFilter` muafiyeti: endpoint `/api/v1/auth/**` prefix'inde olduğu için `shouldNotFilter` kapsamında.

## Servis Katmanı

- **`TenantProvisioningService.provisionTenant(request)`** — TEK fazlı SENKRON akış:
  1. `validateUnique` (subdomain + emailDomain benzersizlik)
  2. `createSchema` (raw JDBC `CREATE SCHEMA`)
  3. `runTenantMigrations` (programmatik Flyway)
  4. `createCompany` (status=`ACTIVE`)
  5. `createAdminUser` (tenant context set + finally clear)
- DDL (`CREATE SCHEMA`) PostgreSQL'de implicit commit -> transaction DIŞI. **Mevcut durumda `provisionTenant` `@Transactional` DEĞİL** — kısmi write riski ([DEBT-10](../docs/DECISIONS.md#debt-10--provisiontenant-transactionsuz)). K-21 uygulandığında refactor edilir (`createPendingCompany` + `verifyAndProvision` her ikisi transactional).
- Lookup'larda `@Transactional(readOnly=true)` kullanılmalı.

## Konfigürasyon

Config profilleri (dev/prod/test) tek source: [ARCHITECTURE.md - Konfigürasyon Profilleri](../docs/ARCHITECTURE.md#konfigurasyon-profilleri).

- `application.yaml` (temel) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Aktif profil `SPRING_PROFILES_ACTIVE` (default `dev`).
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.systemforge.entity")` + `@EnableJpaRepositories("com.ibrhalil.systemforge.persistence.repository")` + `@EnableJpaAuditing` + Hibernate multi-tenancy bean'leri + `DateTimeProvider` (UTC, [RISK-15](../docs/DECISIONS.md#risk-15--datetimeprovider-bug-cozuldu)) + `AuditorAware` (hardcoded `"system"`).

## Gotcha'lar

- **`AuditorAware` hardcoded `"system"`** ([RISK-3](../docs/DECISIONS.md#risk-3--auditoraware-hardcoded-system)) — auth kurulunca SecurityContext'ten gerçek userId alınmalı. Signup endpoint'leri her zaman `"system"` ile audit edilir (kimliği doğrulanmış kullanıcı yok, tenant signup context'i) — beklenen durum.
- **`SecurityConfig`** şu an yalnız `BCryptPasswordEncoder` bean'i tanımlı (strength 10); tam `SecurityFilterChain` YOK. **Önemli:** `spring-boot-starter-security` bağımlılığı da YOK — sadece `spring-security-crypto` (BCrypt için). Yani SecurityFilterChain kurulamaz bile; Faz 2.3'te starter eklenip tek PR'da setup yapılır ([ROADMAP Epic 2.3](../docs/ROADMAP.md#epic-23--spring-security-core-tek-pr)). Signup endpoint'i `TenantFilter` muafiyetinde olduğu için açık.
- BCrypt strength 10 (hedef 12 — [RISK-13](../docs/DECISIONS.md#risk-13--bcrypt-strength)).
- CORS henüz yok (Faz 2.3). Vite proxy dev'de gizliyor, prod'da kırılır.
- `CompanyStatus` enum: `PROVISIONING`, `ACTIVE`, `SUSPENDED`, `TERMINATED`. **Mevcut kod yalnız `ACTIVE` kullanır** (`provisionTenant` direkt ACTIVE set eder). `PROVISIONING` K-21 (Epic 2.0.C) uygulandığında devreye girer.
