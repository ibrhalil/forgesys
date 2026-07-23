package com.ibrhalil.forgesys.persistence.tenant;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant()
            .filter(tenant -> !tenant.isBlank())
            .orElse(DEFAULT_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
