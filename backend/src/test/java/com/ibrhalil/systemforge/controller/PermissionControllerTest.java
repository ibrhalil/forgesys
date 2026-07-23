package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.entity.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PermissionControllerTest extends AbstractRbacWebTest {

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/permissions").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsSeededPermissionsOrderedByName() throws Exception {
        Permission a = new Permission();
        a.setName("iam:role:read");
        entityManager.persist(a);
        Permission b = new Permission();
        b.setName("iam:user:read");
        entityManager.persist(b);

        mockMvc.perform(get("/api/v1/permissions").cookie(auth("reader@tenant.test", "iam:permission:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("iam:role:read"))
                .andExpect(jsonPath("$[1].name").value("iam:user:read"));
    }
}
