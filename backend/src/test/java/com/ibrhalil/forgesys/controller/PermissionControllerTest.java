package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PermissionControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    /* ── list ── */

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
                .andExpect(jsonPath("$.data[0].name").value("iam:role:read"))
                .andExpect(jsonPath("$.data[1].name").value("iam:user:read"));
    }

    /* ── get ── */

    @Test
    void getByIdReturnsPermission() throws Exception {
        Permission p = new Permission();
        p.setName("custom:thing:read");
        p.setDescription("Read things");
        entityManager.persist(p);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/permissions/" + p.getId())
                        .cookie(auth("reader@tenant.test", "iam:permission:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("custom:thing:read"))
                .andExpect(jsonPath("$.description").value("Read things"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/permissions/" + java.util.UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:permission:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── create ── */

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:permission:write"))
                        .content("""
                                {"name":"custom:thing:read","description":"Read things"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("custom:thing:read"))
                .andExpect(jsonPath("$.description").value("Read things"));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:permission:read"))
                        .content("""
                                {"name":"custom:thing:read"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createRejectsBadNamePattern() throws Exception {
        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:permission:write"))
                        .content("""
                                {"name":"Not a permission"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void createDuplicateNameReturns400() throws Exception {
        Permission existing = new Permission();
        existing.setName("custom:thing:read");
        entityManager.persist(existing);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:permission:write"))
                        .content("""
                                {"name":"custom:thing:read"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("permission_name_taken"));
    }

    /* ── update ── */

    @Test
    void updateReturns200() throws Exception {
        Permission p = new Permission();
        p.setName("custom:thing:read");
        entityManager.persist(p);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/permissions/" + p.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:permission:write"))
                        .content("""
                                {"name":"custom:thing:write","description":"Write things"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("custom:thing:write"))
                .andExpect(jsonPath("$.description").value("Write things"));
    }

    /* ── delete ── */

    @Test
    void deleteReturns204WhenUnused() throws Exception {
        Permission p = new Permission();
        p.setName("custom:thing:read");
        entityManager.persist(p);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/permissions/" + p.getId())
                        .cookie(auth("deleter@tenant.test", "iam:permission:delete")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteForbiddenWithoutDeletePermission() throws Exception {
        Permission p = new Permission();
        p.setName("custom:thing:read");
        entityManager.persist(p);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/permissions/" + p.getId())
                        .cookie(auth("writer@tenant.test", "iam:permission:write")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void deleteBlocksWhenPermissionIsAssignedToRole() throws Exception {
        Permission p = new Permission();
        p.setName("custom:thing:read");
        entityManager.persist(p);
        Role role = new Role();
        role.setName("thing-reader");
        role.getPermissions().add(p);
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/permissions/" + p.getId())
                        .cookie(auth("deleter@tenant.test", "iam:permission:delete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("permission_in_use"));
    }
}
