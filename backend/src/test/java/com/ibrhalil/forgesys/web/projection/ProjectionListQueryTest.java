package com.ibrhalil.forgesys.web.projection;

import com.ibrhalil.forgesys.dto.FilterCriteria;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserAccount_;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.entity.UserProfile_;
import com.ibrhalil.forgesys.entity.User_;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterOperator;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end engine + executor verification on H2 (K-49 phase 1): a user-directory
 * style projection over {@link User} exercising all four field kinds — direct
 * ({@code email}), to-one joined ({@code firstName}/{@code enabled}), subquery count
 * ({@code roleCount}) and membership ({@code roleIds}) — through
 * {@link ProjectionListQuery} with filter, sort, paging and {@code qFields} targeting.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectionListQueryTest {

    record UserRow(UUID id, String email, String firstName, boolean enabled, long roleCount) {
    }

    private static final FilterFieldSet FIELDS = FilterFieldSet.builder()
            .field(User_.EMAIL, FilterFieldType.STRING, true)
            .joinedField("firstName", FilterFieldType.STRING, true, User_.USER_PROFILE, UserProfile_.FIRST_NAME)
            .joinedField("enabled", FilterFieldType.BOOLEAN, false, User_.USER_ACCOUNT, UserAccount_.ENABLED)
            .subqueryField("roleCount", FilterFieldType.NUMERIC, false, ProjectionListQueryTest::roleCount)
            .membershipField("roleIds", User_.ROLES, BaseEntity_.ID)
            .build();

    @PersistenceContext
    EntityManager entityManager;

    /* ── shared subquery: count of the user's live roles ── */

    private static Expression<Long> roleCount(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> sq = query.subquery(Long.class);
        Join<?, ?> roles = sq.correlate(root).join(User_.ROLES, JoinType.LEFT);
        return sq.select(cb.count(roles));
    }

    /* ── seeding ── */

    private User seedUser(String email, String firstName) {
        User user = new User();
        user.setUsername(email.substring(0, email.indexOf('@')));
        user.setEmail(email);
        user.setPassword("$2a$12$dummyHashForTestingOnly00000000000000000000000000000");
        UserAccount account = new UserAccount();
        account.setUser(user);
        user.setUserAccount(account);
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(firstName);
        profile.setLastName("User");
        user.setUserProfile(profile);
        entityManager.persist(user);
        return user;
    }

    private Role seedRole(String name) {
        Role role = new Role();
        role.setName(name);
        entityManager.persist(role);
        return role;
    }

    private Page<UserRow> run(Specification<User> spec, Sort sort) {
        Pageable pageable = PageRequest.of(0, 10, sort);
        return ProjectionListQuery.execute(entityManager, User.class, UserRow.class, FIELDS,
                (root, query, cb) -> cb.construct(UserRow.class,
                        root.get(BaseEntity_.ID),
                        root.get(User_.EMAIL),
                        root.join(User_.USER_PROFILE, JoinType.LEFT).get(UserProfile_.FIRST_NAME),
                        root.join(User_.USER_ACCOUNT, JoinType.LEFT).get(UserAccount_.ENABLED),
                        roleCount(root, query, cb)),
                spec, pageable);
    }

    /* ── tests ── */

    @Test
    void projectsAndFiltersAndSortsWithSubqueryField() {
        Role admin = seedRole("admin");
        Role dev = seedRole("dev");
        User alice = seedUser("alice@tenant.test", "Alice");
        User bob = seedUser("bob@tenant.test", "Bob");
        seedUser("carol@tenant.test", "Carol");
        alice.getRoles().add(admin);
        alice.getRoles().add(dev);
        bob.getRoles().add(dev);
        entityManager.flush();

        Specification<User> spec = FilterSpecifications.from(FIELDS, null,
                List.of(new FilterCriteria("roleCount", FilterOperator.GTE, List.of("1"))));
        Page<UserRow> page = run(spec, Sort.by(Sort.Direction.DESC, "roleCount").and(Sort.by("email")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserRow::email)
                .containsExactly("alice@tenant.test", "bob@tenant.test");
        assertThat(page.getContent()).extracting(UserRow::roleCount).containsExactly(2L, 1L);
        assertThat(page.getContent()).extracting(UserRow::firstName).containsExactly("Alice", "Bob");
        assertThat(page.getContent()).allMatch(UserRow::enabled);
    }

    @Test
    void filtersAndSortsOnJoinedField() {
        seedUser("alice@tenant.test", "Alice");
        seedUser("bob@tenant.test", "Bob");
        seedUser("alina@tenant.test", "Alina");
        entityManager.flush();

        Specification<User> spec = FilterSpecifications.from(FIELDS, null,
                List.of(new FilterCriteria("firstName", FilterOperator.CONTAINS, List.of("ali"))));
        Page<UserRow> page = run(spec, Sort.by("firstName"));

        assertThat(page.getContent()).extracting(UserRow::email)
                .containsExactly("alice@tenant.test", "alina@tenant.test");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void membershipFiltersContainsAndEmpty() {
        Role admin = seedRole("admin");
        User alice = seedUser("alice@tenant.test", "Alice");
        seedUser("bob@tenant.test", "Bob");
        User carol = seedUser("carol@tenant.test", "Carol");
        alice.getRoles().add(admin);
        carol.getRoles().add(seedRole("other"));
        entityManager.flush();

        Page<UserRow> withAdmin = run(FilterSpecifications.from(FIELDS, null,
                List.of(new FilterCriteria("roleIds", FilterOperator.IN, List.of(admin.getId().toString())))),
                Sort.by("email"));
        assertThat(withAdmin.getContent()).extracting(UserRow::email).containsExactly("alice@tenant.test");

        Page<UserRow> withoutAdmin = run(FilterSpecifications.from(FIELDS, null,
                List.of(new FilterCriteria("roleIds", FilterOperator.NOT_IN, List.of(admin.getId().toString())))),
                Sort.by("email"));
        assertThat(withoutAdmin.getContent()).extracting(UserRow::email)
                .containsExactly("bob@tenant.test", "carol@tenant.test");

        Page<UserRow> noRoles = run(FilterSpecifications.from(FIELDS, null,
                List.of(new FilterCriteria("roleIds", FilterOperator.IS_NULL, List.of()))),
                Sort.by("email"));
        assertThat(noRoles.getContent()).extracting(UserRow::email).containsExactly("bob@tenant.test");
    }

    @Test
    void qRestrictedToSelectedQFieldsTargetsOnlyThoseColumns() {
        seedUser("a@tenant.test", "Alice");
        seedUser("b@tenant.test", "Bob");
        seedUser("alice@tenant.test", "Zoe");
        entityManager.flush();

        Page<UserRow> allFields = run(FilterSpecifications.from(FIELDS, "ali", null, List.of()), Sort.by("email"));
        assertThat(allFields.getContent()).extracting(UserRow::email)
                .containsExactly("a@tenant.test", "alice@tenant.test");

        Page<UserRow> firstNameOnly = run(
                FilterSpecifications.from(FIELDS, "ali", List.of("firstName"), List.of()), Sort.by("email"));
        assertThat(firstNameOnly.getContent()).extracting(UserRow::email).containsExactly("a@tenant.test");
    }
}
