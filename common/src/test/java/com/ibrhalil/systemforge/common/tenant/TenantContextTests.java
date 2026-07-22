package com.ibrhalil.systemforge.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTests {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTenantContextSetAndGet() {
        String testTenant = "test-tenant-123";
        TenantContext.setCurrentTenant(testTenant);

        Optional<String> currentTenant = TenantContext.getCurrentTenant();

        assertTrue(currentTenant.isPresent(), "Tenant context should contain not be empty");
        assertEquals(testTenant, currentTenant.get());
    }

    @Test
    void testTenantContextClear() {
        TenantContext.setCurrentTenant("some-tenant");
        TenantContext.clear();

        assertTrue(TenantContext.getCurrentTenant().isEmpty(), "Clearing tenant context should result in an empty Optional");
    }

    @Test
    void testInitialStateIsEmpty() {
        assertTrue(TenantContext.getCurrentTenant().isEmpty(), "Beginning with an empty context, getCurrentTenant should return empty");
    }
}
