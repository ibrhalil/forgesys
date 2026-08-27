package com.ibrhalil.forgesys.config;

import java.util.List;

/**
 * K-50 platform permission catalog — lives in code only (frozen #2 pattern), NEVER
 * seeded into tenant schemas. HUMAN platform users implicitly hold ALL of these;
 * SERVICE accounts carry an explicit subset as API-key scopes. The single owner of
 * {@code platform:*} names since F3 (RISK-18 closed) — {@link PermissionCatalog}
 * seeds {@code iam:*} only.
 */
public final class PlatformPermissionCatalog {

    public static final String PLATFORM_COMPANY_READ = "platform:company:read";
    public static final String PLATFORM_COMPANY_WRITE = "platform:company:write";
    public static final String PLATFORM_TENANT_ACCESS = "platform:tenant:access";
    public static final String PLATFORM_TENANT_LIFECYCLE = "platform:tenant:lifecycle";
    public static final String PLATFORM_TENANT_REPORT = "platform:tenant:report";
    public static final String PLATFORM_SERVICE_ACCOUNT_MANAGE = "platform:service-account:manage";
    public static final String PLATFORM_AUDIT_READ = "platform:audit:read";
    public static final String PLATFORM_MAIL_TEST = "platform:mail:test";

    public static final List<String> ALL_NAMES = List.of(
            PLATFORM_COMPANY_READ,
            PLATFORM_COMPANY_WRITE,
            PLATFORM_TENANT_ACCESS,
            PLATFORM_TENANT_LIFECYCLE,
            PLATFORM_TENANT_REPORT,
            PLATFORM_SERVICE_ACCOUNT_MANAGE,
            PLATFORM_AUDIT_READ,
            PLATFORM_MAIL_TEST
    );

    private PlatformPermissionCatalog() {
    }
}
