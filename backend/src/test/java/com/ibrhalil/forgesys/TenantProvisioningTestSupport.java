package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import com.ibrhalil.forgesys.service.mail.InMemoryMailSender;

/**
 * Tenant fixture helper for the gated PG ITs (K-50 F3): drives the REAL two-phase
 * signup flow — phase 1 ({@code createPendingCompany}) + the emailed verification
 * link ({@code InMemoryMailSender}) + phase 2 ({@code verifyAndProvision}) — instead
 * of the removed K-24 bootstrap auto-verify. RISK-26 coverage (the admin user lands
 * in the tenant schema via the REQUIRES_NEW mid-transaction context switch) is
 * preserved because phase 2 runs the very same code path.
 */
final class TenantProvisioningTestSupport {

    private TenantProvisioningTestSupport() {
    }

    /** Provisions one tenant through the production flow; repeatable (delivered index). */
    static CompanyVerifyResponse provisionViaTwoPhaseFlow(TenantProvisioningService service,
                                                          InMemoryMailSender mailSender,
                                                          CompanyRegisterRequest request) {
        int deliveredBefore = mailSender.getDelivered().size();
        service.createPendingCompany(request);
        String actionUrl = mailSender.getDelivered().get(deliveredBefore).actionUrl();
        String rawToken = actionUrl.substring(actionUrl.indexOf("token=") + "token=".length());
        return service.verifyAndProvision(rawToken);
    }
}
