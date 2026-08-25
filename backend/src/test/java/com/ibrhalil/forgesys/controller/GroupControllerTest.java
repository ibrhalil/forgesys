package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
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
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/11111111-1111-1111-1111-111111111111"))
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

        User member = seedRbacUser("member@tenant.test", "member1");
        member.getGroups().add(group);
        entityManager.merge(member);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/groups").cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("engineering"))
                .andExpect(jsonPath("$.data[0].roles[0].name").value("viewer"))
                .andExpect(jsonPath("$.data[0].memberCount").value(1));
    }

    @Test
    void listWithQFiltersByName() throws Exception {
        Group eng = new Group();
        eng.setName("engineering_probe");
        entityManager.persist(eng);
        Group sales = new Group();
        sales.setName("sales_probe");
        entityManager.persist(sales);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/groups").param("q", "engineering")
                        .cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItem("engineering_probe")))
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("sales_probe"))));
    }

    @Test
    void listWithNestedSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/groups").param("sort", "roles.name")
                        .cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
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

    /* ── effective-permissions ── */

    @Test
    void effectivePermissionsResolvesGroupRolePermissions() throws Exception {
        Permission perm = new Permission();
        perm.setName("pm:project:read");
        entityManager.persist(perm);
        Role role = new Role();
        role.setName("viewer");
        role.getPermissions().add(perm);
        entityManager.persist(role);
        Group group = new Group();
        group.setName("engineering");
        group.getRoles().add(role);
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/groups/" + group.getId() + "/effective-permissions")
                        .cookie(auth("reader@tenant.test", "iam:group:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("pm:project:read"));
    }

    /* ── setRoles ── */

    @Test
    void setRolesReplacesGroupRoleSet() throws Exception {
        seedAdmin();
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
        seedAdmin();
        Group group = new Group();
        group.setName("devops");
        entityManager.persist(group);

        User user = seedRbacUser("alice@tenant.test", "alice");
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
        seedAdmin();
        Group group = new Group();
        group.setName("tmp");
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/groups/" + group.getId())
                        .cookie(auth("deleter@tenant.test", "iam:group:delete")))
                .andExpect(status().isNoContent());
    }

    /* ── last-admin guard ── */

    @Test
    void removingLastAdminFromAdminCarryingGroupReturns409() throws Exception {
        // The group is the tenant's only source of admin capacity: it carries the only
        // all_permissions role and its only member is the only enabled holder.
        Role adminRole = new Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);
        Group group = new Group();
        group.setName("admins");
        group.getRoles().add(adminRole);
        entityManager.persist(group);
        User member = seedRbacUser("admin@tenant.test", "admin");
        member.getGroups().add(group);
        entityManager.merge(member);
        entityManager.flush();

        mockMvc.perform(put("/api/v1/groups/" + group.getId() + "/members")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:group:write"))
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    @Test
    void deleteLastAdminCarryingGroupReturns409() throws Exception {
        Role adminRole = new Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);
        Group group = new Group();
        group.setName("admins");
        group.getRoles().add(adminRole);
        entityManager.persist(group);
        User member = seedRbacUser("admin@tenant.test", "admin");
        member.getGroups().add(group);
        entityManager.merge(member);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/groups/" + group.getId())
                        .cookie(auth("deleter@tenant.test", "iam:group:delete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    /* ── K-49: count subqueries, direct + inverse membership, qFields ── */

    @Test
    void searchFiltersAndSortsByMemberCount() throws Exception {
        Group empty = new Group();
        empty.setName("a-empty");
        entityManager.persist(empty);
        Group busy = new Group();
        busy.setName("b-busy");
        entityManager.persist(busy);
        Group loaded = new Group();
        loaded.setName("c-loaded");
        entityManager.persist(loaded);
        User u1 = seedRbacUser("m1@tenant.test", "m1");
        u1.getGroups().add(busy);
        User u2 = seedRbacUser("m2@tenant.test", "m2");
        u2.getGroups().add(loaded);
        User u3 = seedRbacUser("m3@tenant.test", "m3");
        u3.getGroups().add(loaded);
        entityManager.merge(u1);
        entityManager.merge(u2);
        entityManager.merge(u3);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/groups/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"filters":[{"field":"memberCount","operator":"GTE","values":["1"]}],
                                 "sorts":[{"field":"memberCount","direction":"desc"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[0].name").value("c-loaded"))
                .andExpect(jsonPath("$.data[0].memberCount").value(2))
                .andExpect(jsonPath("$.data[0].members[*].email").value(hasItem("m2@tenant.test")))
                .andExpect(jsonPath("$.data[1].name").value("b-busy"))
                .andExpect(jsonPath("$.data[1].memberCount").value(1));
    }

    @Test
    void searchFiltersByInverseMemberMembership() throws Exception {
        Group engineering = new Group();
        engineering.setName("engineering");
        entityManager.persist(engineering);
        Group sales = new Group();
        sales.setName("sales");
        entityManager.persist(sales);
        User alice = seedRbacUser("inv-alice@tenant.test", "ialice");
        alice.getGroups().add(engineering);
        entityManager.merge(alice);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/groups/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"filters":[{"field":"memberIds","operator":"IN","values":["%s"]}]}"""
                                .formatted(alice.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("engineering"));

        // IS_NULL = group has no members at all
        mockMvc.perform(post("/api/v1/groups/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"filters":[{"field":"memberIds","operator":"IS_NULL","values":[]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItem("sales")))
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("engineering"))));
    }

    @Test
    void searchFiltersByRoleMembershipAndRejectsMembershipSort() throws Exception {
        Role viewer = new Role();
        viewer.setName("viewer");
        entityManager.persist(viewer);
        Group carriers = new Group();
        carriers.setName("carriers");
        carriers.getRoles().add(viewer);
        entityManager.persist(carriers);
        Group plain = new Group();
        plain.setName("plain");
        entityManager.persist(plain);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/groups/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"filters":[{"field":"roleIds","operator":"IN","values":["%s"]}]}"""
                                .formatted(viewer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("carriers"));

        mockMvc.perform(post("/api/v1/groups/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .content("""
                                {"sorts":[{"field":"memberIds","direction":"asc"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void getQNarrowedToSelectedQFieldsOnly() throws Exception {
        Group engineering = new Group();
        engineering.setName("engineering");
        engineering.setDescription("night crew");
        entityManager.persist(engineering);
        Group nightOwls = new Group();
        nightOwls.setName("dayshift");
        nightOwls.setDescription("engineering day crew");
        entityManager.persist(nightOwls);
        entityManager.flush();

        // plain q searches name OR description -> both match
        mockMvc.perform(get("/api/v1/groups")
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .param("q", "engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));

        // qFields=name -> only the one whose NAME contains the term
        mockMvc.perform(get("/api/v1/groups")
                        .cookie(auth("reader@tenant.test", "iam:group:read"))
                        .param("q", "engineering")
                        .param("qFields", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].name").value("engineering"));
    }
}
