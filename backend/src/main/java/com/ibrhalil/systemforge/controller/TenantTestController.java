package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A simple test controller to demonstrate and verify the multi-tenant context propagation.
 */
@RestController
@RequestMapping("/api/v1/tenant-test")
public class TenantTestController {

    @GetMapping
    public Map<String, String> getTenantTest() {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            return Map.of("status", "success", "message", "No tenant context provided (running in global scope).");
        }
        return Map.of(
                "status", "success",
                "message", "Request context set to tenant: " + currentTenant,
                "tenantId", currentTenant
        );
    }
}
