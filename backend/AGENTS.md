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
- `notification/` — **[K-21]** `VerificationSender` interface + profile bazlı impl'ler (tenant signup doğrulama kanalı).
- `dto/` — request/response DTO'ları (`record`).
- `exception/` — `GlobalExceptionHandler`, `ErrorResponse`.
- `config/` — `MultiTenancyJpaConfig`, `SecurityConfig`.

## Tenant Context Kuralları (KRİTİK)

- `TenantFilter` (`OncePerRequestFilter`): Host header → subdomain → `CompanyRepository.findBySubdomain` → `schemaName` → `TenantContext`. Sadece `ACTIVE` durumdaki Company'ler çözümlenir (`PROVISIONING`/`SUSPENDED`/`TERMINATED` çözümlenmez).
- `shouldNotFilter()` ile `/api/v1/auth/**` ve `/actuator/**` muaf (tenant bağlamı gerektirmez). **Özellikle `/api/v1/auth/company/register` ve `/api/v1/auth/company/verify` muaf olmak ZORUNDA** — tenant henüz yok (`PROVISIONING`) veya yeni yaratılıyor (K-21).
- **Controller'da tenant doğrulama YAPMA** — filter tek sorumluluk alanı.
- Tenant context null ise resolver `"public"` döner → public şema verilerine (Company, TenantVerificationToken) erişilebilir.
- Programmatik tenant set eden servisler (örn. `TenantProvisioningService.verifyAndProvision` → `createAdminUser`) `finally`'de `TenantContext.clear()` yapmalı.
- `X-Tenant-ID` header fallback **yalnızca `dev` profilinde** aktif. Prod'da tamamen kapalı.
- `@Async` thread'lerinde `TenantContext` otomatik taşınmaz — `TaskDecorator` gerekir (SF-181).

## Endpoint Kuralları

- Tüm endpoint'ler `/api/v1/*` prefix'i altında.
- Response DTO (`record`) olarak dönmeli, entity'ler direkt expose EDİLMEZ.
- Hata yanıtları tek tip `ErrorResponse` (`GlobalExceptionHandler`): `TenantNotFoundException`/`IllegalArgumentException`/validation → 400, generic → 500.
- Bean Validation (`@Valid` + `@NotBlank`/`@Pattern`/`@Email`).

**Tenant Signup Endpoint'leri (K-21, iki fazlı hibrit akış):**

| Method | Path | Açıklama | Auth |
|--------|------|----------|------|
| `POST` | `/api/v1/auth/company/register` | Yeni tenant **signup** — `PROVISIONING` Company + `TenantVerificationToken` yaratır. Şema/migration YOK. `VerificationSender` ile doğrulama linki gönderir (profile bazlı: test→memory, dev→log, prod→mail). | Public |
| `POST` | `/api/v1/auth/company/verify` | Doğrulama token'ını consumes — SENKRON olarak schema CREATE + Flyway tenant migration + admin user yaratır, Company `ACTIVE`'e çeker, token `usedAt` set eder. Ağır işlem (birkaç sn), tek request içinde biter. | Public |

> **Tetikleyici:** PROVISIONING → ACTIVE geçişi polling/event ile DEĞİL, kullanıcının maildeki linke tıklaması (HTTP request) ile senkron olur. Background job gerekmez (opsiyonel cleanup SF-104).

> **TenantFilter muafiyeti:** Her iki endpoint de `/api/v1/auth/**` prefix'inde olduğu için `shouldNotFilter` kapsamında — değişiklik GEREKMEZ. Doğru.

## VerificationSender Abstraction (K-21, SF-100)

Profile bazlı doğrulama kanalı — test edilebilirliği sağlar, mail altyapısına耦合 etmez:

```java
public interface VerificationSender {
    void send(Company company, String token);
}
```

| Profile | Impl | Davranış |
|---------|------|----------|
| `test` | `InMemoryVerificationSender` | Mail atılmaz; token `ConcurrentHashMap`'te. Test assertion için `peek()` |
| `dev` | `LogVerificationSender` | INFO log'a verify link yazılır (terminalden kopyala-yapıştır) |
| `prod` | `MailVerificationSender` (SF-105, ertelendi) | SMTP/SendGrid/SES |

> `spring-boot-starter-mail` pom'a SF-105'e kadar EKLENMEZ. Test/dev impl'leri hiçbir external bağımlılık gerektirmez — build Docker gerektirmez (H2 + Mockito yeterli).

## Servis Katmanı

- Yazma işlerinde `@Transactional` (method-level) ZORUNLU (DEBT-10, SF-001/SF-002). Lookup'larda `@Transactional(readOnly=true)`.
- **`TenantProvisioningService` (K-21 ile refactor, SF-101):**
  - `createPendingCompany(req)` `@Transactional` — `PROVISIONING` Company + `TenantVerificationToken` (public şema, hafif). Şema/migration YOK.
  - `verifyAndProvision(token)` `@Transactional` — token valid → schema CREATE (raw JDBC, DDL implicit commit) + Flyway tenant migration (programmatik) + admin user (tenant context set + finally clear) + Company `ACTIVE` + token `usedAt`. Senkron.
- DDL (`CREATE SCHEMA`) PostgreSQL'de implicit commit → transaction DIŞI. JPA writes atomic, DDL transaction'ın başında veya sonunda çalışır (strategic ordering).

## Konfigürasyon / Profiller

- `application.yaml` (temel) + `application-dev.yaml` + `application-prod.yaml` + `application-test.yaml`. Aktif profil `SPRING_PROFILES_ACTIVE` (default `dev`).
- **`dev`:** PostgreSQL `localhost:5432` (default'lar gömülü, `.env` gerekmez), `ddl-auto=none`, DEBUG, `LogVerificationSender`.
- **`prod`:** PostgreSQL, credential'lar `.env`'den (`.env.example` şablon), INFO, `MailVerificationSender` (SF-105 sonrası).
- **`test`:** H2 in-memory (`MODE=PostgreSQL`), `ddl-auto=create-drop`, `flyway.enabled=false` (Spring Boot 4.1 + Flyway 12'de autoconfig H2 dialect'ı yükleyemiyor — bkz. `docs/ARCHITECTURE.md` Test Profilleri). `@SpringBootTest` `@ActiveProfiles("test")` → build Docker gerektirmez. `InMemoryVerificationSender`. Migration'ların gerçek H2/PostgreSQL test'i `BACKLOG.md` SF-270 (Testcontainers) kapsamında.
- `MultiTenancyJpaConfig`: `@EntityScan("com.ibrhalil.systemforge.entity")` + `@EnableJpaRepositories("com.ibrhalil.systemforge.persistence.repository")` + `@EnableJpaAuditing`. Hibernate multi-tenancy bean'leri burada tanımlanır.

## Gotcha'lar

- **`AuditorAware` hardcoded `"system"` (RISK-3, SF-160)** — auth kurulunca SecurityContext'ten gerçek userId alınmalı. **Ancak signup endpoint'leri (`register`/`verify`) her zaman `"system"` ile audit edilir** — kimliği doğrulanmış kullanıcı yok (tenant signup context'i). Bu beklenen bir durum, bug değil.
- **`SecurityConfig`** şu an yalnız `BCryptPasswordEncoder` bean'i tanımlı; tam `SecurityFilterChain` henüz YOK (Faz 2.3). Signup endpoint'leri zaten `TenantFilter` muafiyetinde olduğu için SecurityFilterChain kurulana kadar açık; SF-072 sonrası `permitAll` olarak explicit configure edilmeli.
- BCrypt strength mevcut 10 (hedef 12 — RISK-13, Faz 2.3).
- CORS henüz yok (Faz 2.3). Vite proxy dev'de gizliyor, prod'da kırılır.
- **`TenantVerificationToken` soft-deletesiz** — `GeneratedIdAuditEntity`'den inherit. `usedAt` ile invalidasyon yapılır, soft-delete gerekmez.
