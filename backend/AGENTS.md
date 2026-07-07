# backend/AGENTS.md

## Modül

Spring Boot uygulaması — controller/service/security/config. `common` + `persistence`'a bağımlı. Sadece bu modül executable jar üretir (`systemforge-backend.jar`). Kök AGENTS.md'deki genel kurallar geçerli.

## Komutlar

```bash
./mvnw -pl backend spring-boot:run                  # çalıştır (root'tan DEĞİL, her zaman -pl backend)
./mvnw -pl backend test -Dtest=ClassName#method     # tek test
./mvnw -pl backend test                              # backend testleri
./mvnw -pl backend -am clean install                 # bağımlılıklarıyla (common+persistence) build
```

> `spring-boot:run` root'tan çalışmaz (root aggregator'dır). Her zaman `-pl backend`.

## Paket Yapısı

Kök paket `com.ibrhalil.systemforge` (`.backend` subpackage DEĞİL):
- `SystemforgeApplication` — main.
- `tenant/` — `TenantFilter` (subdomain çözümleme).
- `controller/` — REST (`/api/v1/*`). [FAZ 3] `modules/` alt paketi.
- `service/` — iş mantığı. [FAZ 3] `modules/` alt paketi.
- `dto/` — request/response DTO'ları (`record`).
- `exception/` — `GlobalExceptionHandler`, `ErrorResponse`.
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig`.

## Tenant Context Kuralları (KRİTİK)

- `TenantFilter` (`OncePerRequestFilter`): Host header → subdomain → `CompanyRepository.findBySubdomain` → `schemaName` → `TenantContext`.
- `shouldNotFilter()` ile `/api/v1/auth/**` ve `/actuator/**` muaf (tenant bağlamı gerektirmez).
- **Controller'da tenant doğrulama YAPMA** — filter tek sorumluluk alanı.
- Tenant context null ise resolver `"public"` döner → public şema verilerine (Company) erişilebilir.
- Programmatik tenant set eden servisler (örn. `TenantProvisioningService.createAdminUser`) `finally`'de `TenantContext.clear()` yapmalı.
- `X-Tenant-ID` header fallback **yalnızca `dev` profilinde** aktif. Prod'da tamamen kapalı.
- `@Async` thread'lerde `TenantContext` otomatik taşınmaz — `TaskDecorator` gerekir (SF-181).

## Endpoint Kuralları

- Tüm endpoint'ler `/api/v1/*` prefix'i altında.
- Response DTO (`record`) olarak dönmeli, entity'ler direkt expose EDİLMEZ.
- Hata yanıtları tek tip `ErrorResponse` (`GlobalExceptionHandler`): `TenantNotFoundException`/`IllegalArgumentException`/validation → 400, generic → 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

**Mevcut endpoint:** `POST /api/v1/auth/company/register` — `TenantProvisioningService` (schema yarat + Flyway tenant migration + admin user).

## Servis Katmanı

- Yazma işlerinde `@Transactional` (method-level) ZORUNLU (DEBT-10). Lookup'larda `@Transactional(readOnly=true)`.
- `TenantProvisioningService.provisionTenant` örnek: schema CREATE + Flyway raw JDBC transaction DIŞI (DDL PostgreSQL'de implicit commit), JPA writes atomic.

## Konfigürasyon / Profiller

- `application.yaml` (temel) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Aktif profil `SPRING_PROFILES_ACTIVE` (default `dev`).
- **`dev`:** PostgreSQL `localhost:5432` (default'lar gömülü, `.env` gerekmez), `ddl-auto=none`, DEBUG.
- **`prod`:** PostgreSQL, credential'lar `.env`'den (`.env.example` şablon), INFO.
- **`test`:** H2 in-memory (`MODE=PostgreSQL`), `ddl-auto=create-drop`, `flyway.enabled=false` (Spring Boot 4.1 + Flyway 12'de autoconfig H2 dialect'ı yükleyemiyor — bkz. `docs/ARCHITECTURE.md` Test Profilleri). `@SpringBootTest` `@ActiveProfiles("test")` → build Docker gerektirmez. Migration'ların gerçek H2/PostgreSQL test'i `BACKLOG.md` SF-270 (Testcontainers) kapsamında.
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.systemforge.entity")` + `@EnableJpaRepositories("com.ibrhalil.systemforge.persistence.repository")` + `@EnableJpaAuditing`. Hibernate multi-tenancy bean'leri burada tanımlanır.

## Gotcha'lar

- **`AuditorAware` hardcoded `"system"` (RISK-3, SF-160)** — auth kurulunca SecurityContext'ten gerçek userId alınmalı.
- **`SecurityConfig`** şu an yalnız `BCryptPasswordEncoder` bean'i tanımlı; tam `SecurityFilterChain` henüz YOK (Faz 2.3).
- BCrypt strength mevcut 10 (hedef 12 — RISK-13, Faz 2.3).
- CORS henüz yok (Faz 2.3). Vite proxy dev'de gizliyor, prod'da kırılır.
