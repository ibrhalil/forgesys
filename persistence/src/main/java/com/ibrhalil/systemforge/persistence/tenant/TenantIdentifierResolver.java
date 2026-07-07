package com.ibrhalil.systemforge.persistence.tenant;

import com.ibrhalil.systemforge.common.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        return (tenant != null && !tenant.isBlank()) ? tenant : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
