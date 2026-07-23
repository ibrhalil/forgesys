package com.ibrhalil.systemforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SystemAdminBootstrapRunnerProfileTest {

    @Autowired(required = false)
    private SystemAdminBootstrapRunner runner;

    @Test
    void runnerIsNotCreatedInTestProfile() {
        assertThat(runner).isNull();
    }
}
