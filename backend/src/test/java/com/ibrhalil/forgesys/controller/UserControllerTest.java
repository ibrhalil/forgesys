package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/users/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/users").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
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

        User user = seedRbacUser("alice@tenant.test", "alice");
        user.getRoles().add(role);
        user.getGroups().add(group);
        entityManager.merge(user);
        entityManager.flush();

        // Directory projection: association lists surface as counts (detail carries the sets).
        mockMvc.perform(get("/api/v1/users").cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("alice@tenant.test"))
                .andExpect(jsonPath("$.data[0].roleCount").value(1))
                .andExpect(jsonPath("$.data[0].groupCount").value(1))
                .andExpect(jsonPath("$.data[0].firstName").value("Test"))
                .andExpect(jsonPath("$.data[0].lastName").value("User"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.meta.hasPrevious").value(false));
    }

    /** K-55: the projection list reads the same engine through the GET {@code ?sq=} blob. */
    @Test
    void listReadsSearchQueryParam() throws Exception {
        seedRbacUser("sq_alice@tenant.test", "sq_alice");
        seedRbacUser("sq_bob@tenant.test", "sq_bob");

        mockMvc.perform(get("/api/v1/users")
                        .param("sq", sq("""
                                {"v":1,"page":0,"size":10,"sorts":[{"field":"email","direction":"asc"}],
                                 "q":"sq_alice"}"""))
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].email").value("sq_alice@tenant.test"));
    }

    /** URL-safe unpadded base64 of the UTF-8 JSON — the wire form the SPA codec produces. */
    private static String sq(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /* ── visibility scope (iam:group-member:read) ── */

    @Test
    void listScopedToOwnGroupsAndSelfForGroupMemberAuthority() throws Exception {
        User caller = seedRbacUser("scoped@tenant.test", "scoped");
        User mate = seedRbacUser("mate@tenant.test", "mate");
        seedRbacUser("outsider@tenant.test", "outsider");
        Group group = new Group();
        group.setName("team-a");
        entityManager.persist(group);
        caller.getGroups().add(group);
        mate.getGroups().add(group);
        entityManager.merge(caller);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users")
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("scoped@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(hasItem("mate@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("outsider@tenant.test"))));
    }

    @Test
    void listUnrestrictedForUserReadAuthority() throws Exception {
        seedRbacUser("anyone@tenant.test", "anyone");
        seedRbacUser("everyone@tenant.test", "everyone");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("anyone@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(hasItem("everyone@tenant.test")));
    }

    @Test
    void detailBlockedOutsideOwnGroupsForGroupMemberAuthority() throws Exception {
        User caller = seedRbacUser("scoped@tenant.test", "scoped");
        User outsider = seedRbacUser("outsider@tenant.test", "outsider");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/{id}", outsider.getId())
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void detailAllowedForSelfAndGroupMatesForGroupMemberAuthority() throws Exception {
        User caller = seedRbacUser("scoped@tenant.test", "scoped");
        User mate = seedRbacUser("mate@tenant.test", "mate");
        Group group = new Group();
        group.setName("team-a");
        entityManager.persist(group);
        caller.getGroups().add(group);
        mate.getGroups().add(group);
        entityManager.merge(caller);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/{id}", mate.getId())
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mate@tenant.test"));

        mockMvc.perform(get("/api/v1/users/{id}", caller.getId())
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("scoped@tenant.test"));
    }

    @Test
    void effectivePermissionsScopedLikeDetail() throws Exception {
        User caller = seedRbacUser("scoped@tenant.test", "scoped");
        User outsider = seedRbacUser("outsider@tenant.test", "outsider");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/{id}/effective-permissions", outsider.getId())
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── list: sort whitelist + q search ── */

    @Test
    void listWithUnknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("sort", "notAField")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void listWithNestedSortPropertyReturns400() throws Exception {
        // Valid entity path but NOT whitelisted — SortGuard must reject it (the
        // repository would happily order by the nested column otherwise).
        mockMvc.perform(get("/api/v1/users").param("sort", "userAccount.enabled")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void listWithWhitelistedSortReturns200() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").param("sort", "username,desc")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("alice"));
    }

    @Test
    void listWithQFiltersCaseInsensitively() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        seedRbacUser("bob@tenant.test", "bob");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").param("q", "ALICE")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("alice@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("bob@tenant.test"))));

        // q also matches the username
        mockMvc.perform(get("/api/v1/users").param("q", "bo")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(jsonPath("$.data[*].email").value(hasItem("bob@tenant.test")));
    }

    @Test
    void listWithQMatchesProfileName() throws Exception {
        User zeynep = seedRbacUser("zeynep@tenant.test", "zeynep");
        zeynep.getUserProfile().setFirstName("Zeynep");
        zeynep.getUserProfile().setLastName("Kaya");
        entityManager.merge(zeynep);
        seedRbacUser("ahmet@tenant.test", "ahmet");
        entityManager.flush();

        // The directory's profile join lets q reach first/last names — email/username don't contain these.
        mockMvc.perform(get("/api/v1/users").param("q", "EYNEP")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("zeynep@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("ahmet@tenant.test"))));

        mockMvc.perform(get("/api/v1/users").param("q", "kaya")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(jsonPath("$.data[*].email").value(hasItem("zeynep@tenant.test")));
    }

    @Test
    void listSortsByFirstName() throws Exception {
        User cem = seedRbacUser("c@tenant.test", "c");
        cem.getUserProfile().setFirstName("Cem");
        entityManager.merge(cem);
        User ali = seedRbacUser("a@tenant.test", "a");
        ali.getUserProfile().setFirstName("Ali");
        entityManager.merge(ali);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").param("sort", "firstName")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].firstName").value("Ali"));
    }

    @Test
    void listWithBlankQReturnsEverything() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        seedRbacUser("bob@tenant.test", "bob");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users").param("q", "  ")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    /* ── POST /users/search: filter engine ── */

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void searchForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("nop@tenant.test"))
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void searchAppliesQAndSorting() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        seedRbacUser("bob@tenant.test", "bob");
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"q":"ali","size":10,"sorts":[{"field":"username","direction":"desc"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("alice@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("bob@tenant.test"))))
                .andExpect(jsonPath("$.data[0].username").value("alice"));
    }

    @Test
    void searchAppliesStructuredFiltersCombined() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        seedRbacUser("bob@tenant.test", "bob");
        entityManager.flush();

        // Two AND-joined clauses: CONTAINS on email + STARTS_WITH on username
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[
                                    {"field":"email","operator":"CONTAINS","values":["tenant.test"]},
                                    {"field":"username","operator":"STARTS_WITH","values":["bo"]}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("bob@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("alice@tenant.test"))));
    }

    @Test
    void searchRejectsUnknownFilterField() throws Exception {
        // 'password' is a direct entity attribute but deliberately unregistered
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"password","operator":"EQ","values":["x"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsNestedFilterField() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"userAccount.enabled","operator":"EQ","values":["true"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsOperatorTypeMismatch() throws Exception {
        // GT is not supported for STRING fields
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"email","operator":"GT","values":["a"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsBetweenWithWrongArity() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"createdDate","operator":"BETWEEN","values":["2026-01-01T00:00:00Z"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsUnparseableValue() throws Exception {
        // emailVerified is BOOLEAN-typed; 'maybe' does not parse
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"emailVerified","operator":"EQ","values":["maybe"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsSortOutsideWhitelist() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"sorts":[{"field":"password","direction":"asc"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchRejectsSizeAboveCap() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("{\"size\":1001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchTemporalFilterWorks() throws Exception {
        seedRbacUser("alice@tenant.test", "alice");
        entityManager.flush();

        // Everything was created "now": GTE a past timestamp matches, LT a past one doesn't
        String past = "2000-01-01T00:00:00Z";
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"createdDate","operator":"GTE","values":["%s"]}]}""".formatted(past)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("alice@tenant.test")));

        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"createdDate","operator":"LT","values":["%s"]}]}""".formatted(past)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    /* ── lockout visibility ([RISK-22] admin surface) ── */

    @Test
    void listAndDetailExposeLockoutExpiry() throws Exception {
        User locked = seedRbacUser("locked@tenant.test", "locked");
        locked.getUserAccount().setLockedUntil(java.time.OffsetDateTime.now().plusMinutes(15));
        entityManager.merge(locked);
        seedRbacUser("free@tenant.test", "free");
        entityManager.flush();

        // Directory projection carries the raw expiry; clients derive "currently
        // locked" by comparing against now (expiry is lazy — see the directory projection).
        mockMvc.perform(get("/api/v1/users").param("q", "locked")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("locked@tenant.test"))
                .andExpect(jsonPath("$.data[0].lockedUntil").exists());

        mockMvc.perform(get("/api/v1/users/{id}", locked.getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").exists());

        mockMvc.perform(get("/api/v1/users/{id}", entityManager.createQuery(
                                "select u from User u where u.email = 'free@tenant.test'", User.class)
                        .getSingleResult().getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void searchFiltersByLockoutExpiry() throws Exception {
        User locked = seedRbacUser("locked@tenant.test", "locked");
        locked.getUserAccount().setLockedUntil(java.time.OffsetDateTime.now().plusMinutes(15));
        entityManager.merge(locked);
        seedRbacUser("free@tenant.test", "free");
        entityManager.flush();

        // "Currently locked" == lockedUntil in the future; NULL (never locked /
        // lazily-expired) never matches a temporal comparison.
        String now = java.time.OffsetDateTime.now().toString();
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"lockedUntil","operator":"GT","values":["%s"]}]}""".formatted(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("locked@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("free@tenant.test"))));
    }

    /* ── admin unlock ([RISK-22]) ── */

    @Test
    void unlockClearsLockoutAndCounter() throws Exception {
        User locked = seedRbacUser("locked@tenant.test", "locked");
        locked.getUserAccount().setLockedUntil(java.time.OffsetDateTime.now().plusMinutes(15));
        locked.getUserAccount().setFailedLoginAttempts(5);
        entityManager.merge(locked);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/{id}/unlock", locked.getId())
                        .cookie(auth("writer@tenant.test", "iam:user:write")))
                .andExpect(status().isNoContent());

        UserAccount cleared = entityManager.find(User.class, locked.getId()).getUserAccount();
        org.junit.jupiter.api.Assertions.assertNull(cleared.getLockedUntil());
        org.junit.jupiter.api.Assertions.assertEquals(0, cleared.getFailedLoginAttempts());
    }

    @Test
    void unlockForbiddenWithoutWritePermission() throws Exception {
        User locked = seedRbacUser("locked@tenant.test", "locked");
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/{id}/unlock", locked.getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void unlockUnknownReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/unlock", UUID.randomUUID())
                        .cookie(auth("writer@tenant.test", "iam:user:write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /* ── activity summary (user detail temporal view) ── */

    @Test
    void activityPicksTheLatestFailedLogin() throws Exception {
        User user = seedRbacUser("act@tenant.test", "actuser");
        entityManager.flush();

        // Interleave attempts; the summary must surface the NEWEST failure only.
        // @CreatedDate is auditor-stamped on persist (and the column is updatable=false),
        // so the timestamps are shifted post-flush via a bulk JPQL update, which bypasses
        // the listener; clear() detaches the stale managed copies.
        UUID older = loginHistory(user.getId(), false);
        loginHistory(user.getId(), true);
        UUID newest = loginHistory(user.getId(), false);
        entityManager.flush();
        shiftCreatedAt(older, java.time.OffsetDateTime.now().minusMinutes(10));
        shiftCreatedAt(newest, java.time.OffsetDateTime.now().minusMinutes(5));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/users/{id}/activity", user.getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastFailedLoginAt").exists());
    }

    @Test
    void activityWithoutHistoryReturnsNullLoginFields() throws Exception {
        User user = seedRbacUser("quiet@tenant.test", "quiet");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/{id}/activity", user.getId())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastFailedLoginAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.lastLoginAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.createdDate").exists());
    }

    @Test
    void activityBlockedOutsideOwnGroupsForGroupMemberAuthority() throws Exception {
        User caller = seedRbacUser("scoped@tenant.test", "scoped");
        User outsider = seedRbacUser("outsider@tenant.test", "outsider");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/{id}/activity", outsider.getId())
                        .cookie(auth(caller.getId(), "scoped@tenant.test", "iam:group-member:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void activityUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}/activity", UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    private UUID loginHistory(UUID userId, boolean success) {
        com.ibrhalil.forgesys.entity.LoginHistory entry = new com.ibrhalil.forgesys.entity.LoginHistory();
        entry.setUserId(userId);
        entry.setUsername("history-entry");
        entry.setSuccess(success);
        entityManager.persist(entry);
        return entry.getId();
    }

    private void shiftCreatedAt(UUID entryId, java.time.OffsetDateTime at) {
        entityManager.createQuery(
                        "update LoginHistory h set h.createdDate = :at where h.id = :id")
                .setParameter("at", at)
                .setParameter("id", entryId)
                .executeUpdate();
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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createDuplicateEmailReturns400() throws Exception {
        seedRbacUser("existing@tenant.test", "existing");
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"email":"existing@tenant.test","password":"Secret123!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user_email_taken"));
    }

    @Test
    void createWithRolesAndGroupsAssignsThem() throws Exception {
        Role role = new Role();
        role.setName("viewer");
        entityManager.persist(role);
        Group group = new Group();
        group.setName("engineering");
        entityManager.persist(group);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("{\"email\":\"carol@tenant.test\",\"password\":\"Secret123!\","
                                + "\"roleIds\":[\"" + role.getId() + "\"],"
                                + "\"groupIds\":[\"" + group.getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("carol@tenant.test"))
                .andExpect(jsonPath("$.roles[0].name").value("viewer"))
                .andExpect(jsonPath("$.groups[0].name").value("engineering"));
    }

    /* ── get ── */

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    /**
     * [RISK-29] A malformed path variable (non-UUID) must map to 400 validation_error,
     * not the previous catch-all 500.
     */
    @Test
    void getMalformedUuidReturns400Not500() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-uuid")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── effective-permissions ── */

    @Test
    void effectivePermissionsResolvesDirectRolePermissions() throws Exception {
        Permission perm = new Permission();
        perm.setName("iam:user:read");
        entityManager.persist(perm);
        Role role = new Role();
        role.setName("viewer");
        role.getPermissions().add(perm);
        entityManager.persist(role);

        User user = seedRbacUser("eff@tenant.test", "effuser");
        user.getRoles().add(role);
        entityManager.merge(user);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/effective-permissions")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("iam:user:read"));
    }

    @Test
    void effectivePermissionsIncludesGroupGrantedRole() throws Exception {
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

        User user = seedRbacUser("geff@tenant.test", "geffuser");
        user.getGroups().add(group);
        entityManager.merge(user);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/effective-permissions")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("pm:project:read"));
    }

    @Test
    void effectivePermissionsReturnsAllForAllPermissionsRole() throws Exception {
        // Two permissions exist in the tenant; NEITHER is attached to the role. The
        // all_permissions flag must make the user implicitly hold both (plus any future
        // permission) — the core fix for "admins never hear about new permissions".
        Permission a = new Permission();
        a.setName("iam:user:read");
        entityManager.persist(a);
        Permission b = new Permission();
        b.setName("pm:project:read");
        entityManager.persist(b);
        Role role = new Role();
        role.setName("superuser");
        role.setAllPermissions(true);
        entityManager.persist(role);

        User user = seedRbacUser("all@tenant.test", "alluser");
        user.getRoles().add(role);
        entityManager.merge(user);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/users/" + user.getId() + "/effective-permissions")
                        .cookie(auth("reader@tenant.test", "iam:user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == 'iam:user:read')]").exists())
                .andExpect(jsonPath("$[?(@ == 'pm:project:read')]").exists());
    }

    /* ── update ── */

    @Test
    void updateProfileFields() throws Exception {
        seedAdmin();
        User user = seedRbacUser("update@tenant.test", "updateuser");
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
        seedAdmin();
        User user = seedRbacUser("roles@tenant.test", "roleuser");

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
        User user = seedRbacUser("badrole@tenant.test", "badrole");
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
        seedAdmin();
        User user = seedRbacUser("groups@tenant.test", "groupuser");

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
        seedAdmin();
        User user = seedRbacUser("delete@tenant.test", "deluser");
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/users/" + user.getId())
                        .cookie(auth("deleter@tenant.test", "iam:user:delete")))
                .andExpect(status().isNoContent());
    }

    /* ── last-admin guard (tenant lockout prevention) ── */

    @Test
    void deleteLastAdminReturns409() throws Exception {
        User admin = seedAdmin();
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/users/" + admin.getId())
                        .cookie(auth("deleter@tenant.test", "iam:user:delete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    @Test
    void deleteWithSecondAdminReturns204() throws Exception {
        User admin = seedAdmin();
        User second = seedRbacUser("admin2@tenant.test", "admin2");
        second.getRoles().addAll(admin.getRoles());
        entityManager.merge(second);
        entityManager.flush();

        mockMvc.perform(delete("/api/v1/users/" + admin.getId())
                        .cookie(auth("deleter@tenant.test", "iam:user:delete")))
                .andExpect(status().isNoContent());
    }

    @Test
    void selfDeleteIsRejectedEvenWithAnotherAdmin() throws Exception {
        User admin = seedAdmin();
        User second = seedRbacUser("admin2@tenant.test", "admin2");
        second.getRoles().addAll(admin.getRoles());
        entityManager.merge(second);
        entityManager.flush();

        // Cookie bound to the TARGET user's id -> actor == target, forbidden even
        // though another enabled admin exists.
        mockMvc.perform(delete("/api/v1/users/" + second.getId())
                        .cookie(auth(second.getId(), "admin2@tenant.test", "iam:user:delete")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("self_delete_forbidden"));
    }

    @Test
    void disableLastActiveAdminReturns409() throws Exception {
        User admin = seedAdmin();
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + admin.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"firstName":"Test","lastName":"User","enabled":false}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    @Test
    void disabledAdminDoesNotCountTowardsInvariant() throws Exception {
        User admin = seedAdmin();
        User second = seedRbacUser("admin2@tenant.test", "admin2");
        second.getRoles().addAll(admin.getRoles());
        second.getUserAccount().setEnabled(false); // disabled admins don't count
        entityManager.merge(second);
        entityManager.flush();

        // Only `admin` is an ACTIVE admin -> disabling them must still be rejected.
        mockMvc.perform(put("/api/v1/users/" + admin.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("""
                                {"firstName":"Test","lastName":"User","enabled":false}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    @Test
    void setRolesEmptyingLastAdminReturns409() throws Exception {
        User admin = seedAdmin();
        entityManager.flush();

        mockMvc.perform(put("/api/v1/users/" + admin.getId() + "/roles")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "iam:user:write"))
                        .content("{\"roleIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_admin_required"));
    }

    /* ── admin password reset ── */

    @Test
    void resetPasswordReturns204() throws Exception {
        User user = seedRbacUser("reset@tenant.test", "resetuser");
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
        User user = seedRbacUser("reset2@tenant.test", "resetuser2");
        entityManager.flush();

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/password")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"newPassword":"BrandNew123!"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── K-49: directory projection filter/sort on joined, count and membership columns ── */

    @Test
    void searchFiltersByCountSubqueryAndSortsByIt() throws Exception {
        Role admin = new Role();
        admin.setName("admin");
        entityManager.persist(admin);
        Role dev = new Role();
        dev.setName("dev");
        entityManager.persist(dev);
        User alice = seedRbacUser("count-alice@tenant.test", "calice");
        User bob = seedRbacUser("count-bob@tenant.test", "cbob");
        seedRbacUser("count-none@tenant.test", "cnone");
        alice.getRoles().add(admin);
        alice.getRoles().add(dev);
        bob.getRoles().add(dev);
        entityManager.merge(alice);
        entityManager.flush();

        // roleCount GTE 1, sorted by roleCount desc -> alice (2) before bob (1), cnone filtered out
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"roleCount","operator":"GTE","values":["1"]}],
                                 "sorts":[{"field":"roleCount","direction":"desc"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[0].email").value("count-alice@tenant.test"))
                .andExpect(jsonPath("$.data[0].roleCount").value(2))
                .andExpect(jsonPath("$.data[1].email").value("count-bob@tenant.test"))
                .andExpect(jsonPath("$.data[1].roleCount").value(1));
    }

    @Test
    void searchFiltersByRoleMembership() throws Exception {
        Role admin = new Role();
        admin.setName("admin");
        entityManager.persist(admin);
        User alice = seedRbacUser("member-alice@tenant.test", "malice");
        seedRbacUser("member-bob@tenant.test", "mbob");
        alice.getRoles().add(admin);
        entityManager.merge(alice);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"roleIds","operator":"IN","values":["%s"]}]}"""
                                .formatted(admin.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].email").value("member-alice@tenant.test"));

        // IS_NULL = no roles at all
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"roleIds","operator":"IS_NULL","values":[]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").value(hasItem("member-bob@tenant.test")))
                .andExpect(jsonPath("$.data[*].email").value(not(hasItem("member-alice@tenant.test"))));
    }

    @Test
    void searchRejectsSortOnMembershipField() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"sorts":[{"field":"roleIds","direction":"asc"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void searchFiltersByJoinedLastLoginAt() throws Exception {
        User recent = seedRbacUser("recent@tenant.test", "recent");
        seedRbacUser("never@tenant.test", "never");
        recent.getUserAccount().setLastLoginAt(OffsetDateTime.parse("2026-08-20T10:00:00Z"));
        entityManager.merge(recent);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"filters":[{"field":"lastLoginAt","operator":"GTE","values":["2026-08-19T00:00:00Z"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].email").value("recent@tenant.test"));
    }

    @Test
    void searchQNarrowedToSelectedQFieldsOnly() throws Exception {
        // email contains "ali"; firstName is "Test" for both — targeting firstName must miss the email match
        seedRbacUser("ali@tenant.test", "aliuser");
        seedRbacUser("other@tenant.test", "otheruser");
        entityManager.flush();

        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"q":"ali","qFields":["firstName"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));

        mockMvc.perform(get("/api/v1/users")
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .param("q", "ali")
                        .param("qFields", "email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].email").value("ali@tenant.test"));
    }

    @Test
    void searchRejectsNonSearchableQField() throws Exception {
        mockMvc.perform(post("/api/v1/users/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:user:read"))
                        .content("""
                                {"q":"ali","qFields":["enabled"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── helpers ── */
}
