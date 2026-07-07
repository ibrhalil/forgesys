# common/AGENTS.md

## Modül

Paylaşılan çekirdek — `TenantContext` (ThreadLocal), paylaşılan exception'lar. Hem web katmanı (`backend.TenantFilter`) hem persistence katmanı (`SchemaPerTenantConnectionProvider`) tarafından ortak kullanılır. Kök AGENTS.md'deki genel kurallar geçerli.

## Kurallar (ZORUNLU)

- **Spring/JPA bağımlılığı YASAK.** Bu modül lightweight çekirdektir; `pom.xml`'de yalnızca `slf4j-api` (compile) + `junit-jupiter` (test) olmalı. `spring-boot-starter-*` EKLEME — modül amacını bozar.
- Paket: `com.ibrhalil.systemforge.common.*` — `tenant/`, `exception/` alt paketleri.
- Buraya konan tip birden fazla modülce paylaşılıyorsa doğru yerdir; yalnızca `backend`'in kullandığı tipler buraya DEĞİL `backend`'e konur.

## Mevcut

- `common/tenant/TenantContext` — `final` utility sınıf, `ThreadLocal<String>`. Static metotlar: `setCurrentTenant(String)`, `getCurrentTenant()`, `clear()`. Her istekte `TenantFilter` set eder, `finally`'de `clear()` çağrılmalı (ThreadLocal leak yok).
- `common/exception/TenantNotFoundException` — `RuntimeException`. Persistence fırlatır, `backend.GlobalExceptionHandler` HTTP 400'e map eder.

## Test

```bash
./mvnw -pl common test
```

`TenantContext` birim testleri mevcut. Yeni ThreadLocal kullanan kodda leak testi ekle.
