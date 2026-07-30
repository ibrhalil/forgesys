package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/roles").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsRolesWithNestedPermissions() throws Exception {
        Permission permission = new Permission();
        permission.setName("iam:user:read");
        entityManager.persist(permission);

        Role role = new Role();
        role.setName("editor");
        role.getPermissions().add(permission);
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/roles").cookie(auth("reader@tenant.test", "iam:role:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("editor"))
                .andExpect(jsonPath("$.data[0].permissions[0].name").value("iam:user:read"));
    }

    @Test
    void listWithQFiltersByName() throws Exception {
        Role editor = new Role();
        editor.setName("editor_probe");
        entityManager.persist(editor);
        Role viewer = new Role();
        viewer.setName("viewer_probe");
        entityManager.persist(viewer);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/roles").param("q", "editor")
                        .cookie(auth("reader@tenant.test", "iam:role:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItem("editor_probe")))
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("viewer_probe"))));
    }

    @Test
    void listWithNestedSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/roles").param("sort", "permissions.name")
                        .cookie(auth("reader@tenant.test", "iam:role:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("""
                                {"name":"viewer","description":"Read-only access"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("viewer"))
                .andExpect(jsonPath("$.description").value("Read-only access"));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:role:read"))
                        .content("""
                                {"name":"viewer"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createDuplicateNameReturns400() throws Exception {
        Role existing = new Role();
        existing.setName("editor");
        entityManager.persist(existing);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("""
                                {"name":"editor"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("role_name_taken"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:role:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void setPermissionsReplacesRolePermissionSet() throws Exception {
        seedAdmin();
        Role role = new Role();
        role.setName("editor");
        entityManager.persist(role);

        Permission kept = new Permission();
        kept.setName("iam:user:read");
        entityManager.persist(kept);
        Permission dropped = new Permission();
        dropped.setName("iam:role:read");
        entityManager.persist(dropped);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"permissionIds\":[\"" + kept.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0].name").value("iam:user:read"));
    }

    @Test
    void setPermissionsWithUnknownIdReturns404() throws Exception {
        Role role = new Role();
        role.setName("editor");
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"permissionIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── all_permissions flag (ALL shortcut) ── */

    @Test
    void setPermissionsWithAllSetsFlagAndClearsExplicit() throws Exception {
        seedAdmin();
        Role role = new Role();
        role.setName("editor");
        Permission attached = new Permission();
        attached.setName("iam:user:read");
        entityManager.persist(attached);
        role.getPermissions().add(attached);
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"all\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allPermissions").value(true))
                .andExpect(jsonPath("$.permissions.length()").value(0));
    }

    @Test
    void setPermissionsAllThenExplicitClearsFlag() throws Exception {
        seedAdmin();
        Role role = new Role();
        role.setName("editor");
        role.setAllPermissions(true);
        entityManager.persist(role);
        Permission perm = new Permission();
        perm.setName("iam:user:read");
        entityManager.persist(perm);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"all\":false,\"permissionIds\":[\"" + perm.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allPermissions").value(false))
                .andExpect(jsonPath("$.permissions[0].name").value("iam:user:read"));
    }

    @Test
    void setPermissionsExplicitWithoutIdsReturns400() throws Exception {
        Role role = new Role();
        role.setName("editor");
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/roles/" + role.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"all\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void deleteReturns204() throws Exception {
        seedAdmin();
        Role role = new Role();
        role.setName("tmp");
        entityManager.persist(role);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/roles/" + role.getId())
                        .cookie(auth("deleter@tenant.test", "iam:role:delete")))
                .andExpect(status().isNoContent());
    }

    /* ── last-admin guard ── */

    @Test
    void deleteLastAdminRoleReturns409() throws Exception {
        // The only all_permissions role, held by the only enabled admin.
        Role adminRole = new Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);
        com.ibrhalil.forgesys.entity.User holder = seedRbacUser("admin@tenant.test", "admin");
        holder.getRoles().add(adminRole);
        entityManager.merge(holder);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/roles/" + adminRole.getId())
                        .cookie(auth("deleter@tenant.test", "iam:role:delete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    @Test
    void clearingAllPermissionsFlagReturns409() throws Exception {
        Role adminRole = new Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);
        com.ibrhalil.forgesys.entity.User holder = seedRbacUser("admin@tenant.test", "admin");
        holder.getRoles().add(adminRole);
        entityManager.merge(holder);
        Permission perm = new Permission();
        perm.setName("iam:user:read");
        entityManager.persist(perm);
        entityManager.flush();

        // Switching the last admin role from ALL to an explicit (non-admin) set drops
        // the tenant below one active admin.
        mockMvc.perform(put("/api/v1/roles/" + adminRole.getId() + "/permissions")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:role:write"))
                        .content("{\"all\":false,\"permissionIds\":[\"" + perm.getId() + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }
}
