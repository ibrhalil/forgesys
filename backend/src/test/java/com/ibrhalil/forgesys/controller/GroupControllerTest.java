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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    /* ── auth / permission gates ── */

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/groups").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── list ── */

    @Test
    void listReturnsGroupsWithNestedRolesAndMemberCount() throws Exception {
        Role role = new Role();
        role.setName("viewer");
        entityManager.persist(role);

        Group group = new Group();
        group.setName("engineering");
        group.setDescription("Engineering team");
        group.getRoles().add(role);
        entityManager.persist(group);

        User member = seedUser("member@tenant.test", "member1");
        member.getGroups().add(group);
        entityManager.merge(member);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/groups").cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("engineering"))
                .andExpect(jsonPath("$.content[0].roles[0].name").value("viewer"))
                .andExpect(jsonPath("$.content[0].memberCount").value(1));
    }

    /* ── create ── */

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("""
                                {"name":"backend","description":"Backend team","active":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("backend"))
                .andExpect(jsonPath("$.description").value("Backend team"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"name":"backend"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createDuplicateNameReturns400() throws Exception {
        Group existing = new Group();
        existing.setName("backend");
        entityManager.persist(existing);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/groups")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("""
                                {"name":"backend"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("group_name_taken"));
    }

    /* ── get ── */

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/groups/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── setRoles ── */

    @Test
    void setRolesReplacesGroupRoleSet() throws Exception {
        Group group = new Group();
        group.setName("qa");
        entityManager.persist(group);

        Role kept = new Role();
        kept.setName("viewer");
        entityManager.persist(kept);
        Role dropped = new Role();
        dropped.setName("editor");
        entityManager.persist(dropped);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/groups/" + group.getId() + "/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("{\"roleIds\":[\"" + kept.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("viewer"));
    }

    @Test
    void setRolesWithUnknownIdReturns404() throws Exception {
        Group group = new Group();
        group.setName("qa");
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/groups/" + group.getId() + "/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("{\"roleIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── setMembers ── */

    @Test
    void setMembersReplacesGroupMembers() throws Exception {
        Group group = new Group();
        group.setName("devops");
        entityManager.persist(group);

        User user = seedUser("alice@tenant.test", "alice");
        entityManager.flush();

        mockMvc.perform(put("/api/v1/groups/" + group.getId() + "/members")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("{\"userIds\":[\"" + user.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    /* ── delete ── */

    @Test
    void deleteReturns204() throws Exception {
        Group group = new Group();
        group.setName("tmp");
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/groups/" + group.getId())
                        .cookie(auth("deleter@tenant.test", "iam:group:delete")))
                .andExpect(status().isNoContent());
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
