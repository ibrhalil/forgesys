package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    /* ── auth / permission gates ── */

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/users").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden());
    }

    /* ── list ── */

    @Test
    void listReturnsUsersWithRolesAndGroups() throws Exception {
        Role role = new Role();
        role.setName("admin");
        entityManager.persist(role);

        Group group = new Group();
        group.setName("engineering");
        entityManager.persist(group);

        User user = seedUser("alice@tenant.test", "alice");
        user.getRoles().add(role);
        user.getGroups().add(group);
        entityManager.merge(user);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("alice@tenant.test"))
                .andExpect(jsonPath("$.content[0].roles[0].name").value("admin"))
                .andExpect(jsonPath("$.content[0].groups[0].name").value("engineering"));
    }

    /* ── create ── */

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"email":"bob@tenant.test","password":"Secret123!","firstName":"Bob","lastName":"Smith"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("bob@tenant.test"))
                .andExpect(jsonPath("$.firstName").value("Bob"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"email":"bob@tenant.test","password":"Secret123!"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDuplicateEmailReturns400() throws Exception {
        seedUser("existing@tenant.test", "existing");
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"email":"existing@tenant.test","password":"Secret123!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_email_taken"));
    }

    /* ── get ── */

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── update ── */

    @Test
    void updateProfileFields() throws Exception {
        User user = seedUser("update@tenant.test", "updateuser");
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + user.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"firstName":"Updated","lastName":"Name","enabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    /* ── setRoles ── */

    @Test
    void setRolesReplacesUserRoleSet() throws Exception {
        User user = seedUser("roles@tenant.test", "roleuser");

        Role kept = new Role();
        kept.setName("viewer");
        entityManager.persist(kept);
        Role dropped = new Role();
        dropped.setName("editor");
        entityManager.persist(dropped);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + user.getId() + "/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("{\"roleIds\":[\"" + kept.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("viewer"));
    }

    @Test
    void setRolesWithUnknownIdReturns404() throws Exception {
        User user = seedUser("badrole@tenant.test", "badrole");
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + user.getId() + "/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("{\"roleIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── setGroups ── */

    @Test
    void setGroupsReplacesUserGroupSet() throws Exception {
        User user = seedUser("groups@tenant.test", "groupuser");

        Group group = new Group();
        group.setName("devops");
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + user.getId() + "/groups")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("{\"groupIds\":[\"" + group.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].name").value("devops"));
    }

    /* ── delete ── */

    @Test
    void deleteReturns204() throws Exception {
        User user = seedUser("delete@tenant.test", "deluser");
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/users/" + user.getId())
                        .cookie(auth("deleter@tenant.test", "iam:user:delete")))
                .andExpect(status().isNoContent());
    }

    /* ── admin password reset ── */

    @Test
    void resetPasswordReturns204() throws Exception {
        User user = seedUser("reset@tenant.test", "resetuser");
        entityManager.flush();

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/password")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"newPassword":"BrandNew123!"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPasswordForbiddenWithoutWritePermission() throws Exception {
        User user = seedUser("reset2@tenant.test", "resetuser2");
        entityManager.flush();

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/password")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"newPassword":"BrandNew123!"}"""))
                .andExpect(status().isForbidden());
    }

    /* ── helpers ── */

    private User seedUser(String email, String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$12$dummyHashForTestingOnly00000000000000000000000000000");
        user.setEmailVerified(false);

        UserAccount account = new UserAccount();
        account.setUser(user);
        user.setUserAccount(account);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("User");
        user.setUserProfile(profile);

        entityManager.persist(user);
        return user;
    }
}
