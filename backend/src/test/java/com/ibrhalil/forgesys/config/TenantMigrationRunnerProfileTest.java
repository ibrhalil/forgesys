package com.ibrhalil.forgesys.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TenantMigrationRunnerProfileTest {

    @Autowired(required = false)
    private TenantMigrationRunner runner;

    @Test
    void runnerIsNotCreatedInTestProfile() {
        assertThat(runner).isNull();
    }
}
