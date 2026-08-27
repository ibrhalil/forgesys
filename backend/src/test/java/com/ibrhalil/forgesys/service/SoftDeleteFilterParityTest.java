package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EGH item 4 parity gate: the auto-enabled Hibernate filter on
 * {@code SoftDeleteAuditEntity} must reproduce every default behavior of the former
 * {@code @SQLRestriction} — load-by-id, derived queries, Specifications, JPQL joins
 * and pagination counts all hide {@code is_deleted = true} rows — while the scoped
 * opt-in window ({@code IncludingDeleted} fragments) restores them. If ANY case here
 * fails, the spike reverts per the plan (D4).
 *
 * <p>Soft-deletion is applied via NATIVE SQL + {@code em.clear()} so assertions hit
 * the database, never the persistence context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SoftDeleteFilterParityTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    // --- default parity: soft-deleted rows invisible on every read path ---

    @Test
    void loadByIdHidesSoftDeleted() {
        Company deleted = seedCompany("deleted-co", "deleted-co");
        seedCompany("live-co", "live-co");
        softDelete("t_companies", "id", deleted.getId());

        assertThat(companyRepository.findById(deleted.getId())).isEmpty();
        assertThat(entityManager.find(Company.class, deleted.getId())).isNull();
    }

    @Test
    void derivedQueryListHidesSoftDeleted() {
        seedCompany("live-co", "live-co");
        Company deleted = seedCompany("deleted-co", "deleted-co");
        softDelete("t_companies", "id", deleted.getId());

        assertThat(companyRepository.findAll())
                .extracting(Company::getSubdomain)
                .containsExactly("live-co");
    }

    @Test
    void specificationQueryHidesSoftDeleted() {
        User live = seedUser("live@parity.test", "parity-live");
        User deleted = seedUser("gone@parity.test", "parity-gone");
        UUID deletedId = deleted.getId();
        entityManager.flush();
        softDelete("t_users", "id", deletedId);

        List<User> result = userRepository.findAll(
                (root, query, cb) -> cb.conjunction());

        assertThat(result).extracting(User::getEmail).containsExactly(live.getEmail());
        assertThat(deletedId).isNotNull();
    }

    @Test
    void jpqlJoinHidesSoftDeletedUsers() {
        Role role = new Role();
        role.setName("parity-role-" + UUID.randomUUID());
        entityManager.persist(role);

        User admin = seedUser("admin@parity.test", "parity-admin");
        User victim = seedUser("victim@parity.test", "parity-victim");
        admin.getRoles().add(role);
        victim.getRoles().add(role);
        entityManager.flush();
        softDelete("t_users", "id", victim.getId());

        List<User> members = entityManager.createQuery(
                        "select u from User u join u.roles r where r.id = :roleId", User.class)
                .setParameter("roleId", role.getId())
                .getResultList();

        assertThat(members).extracting(User::getEmail).containsExactly("admin@parity.test");
    }

    @Test
    void paginationCountHidesSoftDeleted() {
        seedCompany("live-co", "live-co");
        Company deleted = seedCompany("deleted-co", "deleted-co");
        softDelete("t_companies", "id", deleted.getId());

        assertThat(companyRepository.findAll(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    // --- opt-in window: IncludingDeleted fragments restore deleted rows, scoped ---

    @Test
    void optInReturnsDeletedCompanyAndRestoresTheFilter() {
        Company deleted = seedCompany("deleted-co", "deleted-co");

        assertThat(companyRepository.findById(deleted.getId())).isPresent(); // live right now
        softDelete("t_companies", "id", deleted.getId());

        assertThat(companyRepository.findByIdIncludingDeleted(deleted.getId())).isPresent();
        assertThat(companyRepository.findAllIncludingDeleted())
                .extracting(Company::getSubdomain)
                .contains("deleted-co");

        // The window closed: default reads hide the row again in the SAME transaction.
        assertThat(companyRepository.findById(deleted.getId())).isEmpty();
    }

    @Test
    void optInReturnsDeletedUser() {
        User victim = seedUser("victim@parity.test", "parity-victim");
        UUID victimId = victim.getId();
        entityManager.flush();
        softDelete("t_users", "id", victimId);

        assertThat(userRepository.findByIdIncludingDeleted(victimId)).isPresent();
        assertThat(userRepository.findAllIncludingDeleted())
                .extracting(User::getEmail)
                .contains("victim@parity.test");

        assertThat(userRepository.findById(victimId)).isEmpty();
    }

    // --- helpers ----------------------------------------------------------

    private Company seedCompany(String name, String subdomain) {
        Company company = new Company();
        company.setName(name);
        company.setSubdomain(subdomain);
        company.setSchemaName("tenant_" + subdomain.replace('-', '_'));
        company.setStatus(CompanyStatus.ACTIVE);
        entityManager.persist(company);
        entityManager.flush();
        return company;
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

        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    /** Native UPDATE + context clear: DB-level soft-delete, no stale managed copies. */
    private void softDelete(String table, String idColumn, UUID id) {
        entityManager.createNativeQuery("update " + table + " set is_deleted = true where " + idColumn + " = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
    }
}
