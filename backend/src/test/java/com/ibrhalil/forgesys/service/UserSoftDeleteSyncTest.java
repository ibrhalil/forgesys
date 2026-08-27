package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EGH item 3 regression lock: soft-deleting a User cascades to its {@code @MapsId}
 * children in the SAME flush — UserAccount and UserProfile rows must end up
 * {@code is_deleted = true} too, otherwise a deleted user's account stays "active"
 * (login/token guards read it). The sync mechanism is JPA {@code cascade = REMOVE}
 * on {@code User.userAccount/userProfile}; each child's own {@code @SQLDelete}
 * runs with its own {@code @Version} check inside the same transaction.
 *
 * <p>Row state is asserted via NATIVE SQL — JPQL/entity reads are filtered by
 * {@code @SQLRestriction} and would hide the soft-deleted rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSoftDeleteSyncTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserService userService;

    @Test
    void deleteUserSoftDeletesAccountAndProfileRowsToo() {
        User admin = seedAdmin();
        User victim = seedUser("victim@tenant.test", "victim");
        UUID victimId = victim.getId();

        userService.delete(victimId);
        entityManager.flush();

        assertThat(isDeleted("t_users", "id", victimId)).isTrue();
        assertThat(isDeleted("t_user_accounts", "user_id", victimId)).isTrue();
        assertThat(isDeleted("t_user_profiles", "user_id", victimId)).isTrue();
        assertThat(isDeleted("t_users", "id", admin.getId())).isFalse();
    }

    @Test
    void secondDeleteOfSameUserIsNotFound() {
        seedAdmin();
        User victim = seedUser("gone@tenant.test", "gone");
        UUID victimId = victim.getId();
        userService.delete(victimId);
        entityManager.flush();

        // @SQLRestriction hides the soft-deleted row -> existsById misses -> 404.
        assertThatThrownBy(() -> userService.delete(victimId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Native read bypasses @SQLRestriction; same tx sees the flushed UPDATEs. */
    private boolean isDeleted(String table, String idColumn, UUID id) {
        Object flag = entityManager.createNativeQuery(
                        "select is_deleted from " + table + " where " + idColumn + " = :id")
                .setParameter("id", id)
                .getSingleResult();
        return Boolean.TRUE.equals(flag);
    }

    /** Last-admin invariant baseline: an all_permissions role held by an enabled user. */
    private User seedAdmin() {
        Role adminRole = new Role();
        adminRole.setName("Admin");
        adminRole.setAllPermissions(true);
        entityManager.persist(adminRole);

        User admin = seedUser("admin@tenant.test", "admin");
        admin.getRoles().add(adminRole);
        entityManager.merge(admin);
        return admin;
    }

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
