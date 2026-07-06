package com.ibrhalil.systemforge.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTests {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTenantContextSetAndGet() {
        String testTenant = "test-tenant-123";
        TenantContext.setCurrentTenant(testTenant);
        
        assertEquals(testTenant, TenantContext.getCurrentTenant());
    }

    @Test
    void testTenantContextClear() {
        TenantContext.setCurrentTenant("some-tenant");
        TenantContext.clear();
        
        assertNull(TenantContext.getCurrentTenant());
    }
}
