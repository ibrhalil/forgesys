package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.RbacSeeder;
import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final String SCHEMA_PREFIX = "tenant_";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final TenantMigrationSupport tenantMigrationSupport;
    // Optional: RbacSeeder is @Profile("!test") — absent in tests, which never exercise provisioning.
    private final ObjectProvider<RbacSeeder> rbacSeederProvider;

    public Company provisionTenant(CompanyRegisterRequest request) {
        log.info("Provisioning new tenant: {}", request.subdomain());

        String schemaName = buildSchemaName(request.subdomain());
        validateUnique(request.subdomain(), request.emailDomain(), schemaName);

        createSchema(schemaName);
        runTenantMigrations(schemaName);

        Company company = createCompany(request, schemaName);
        createAdminUser(schemaName, request);

        log.info("Tenant provisioned successfully: {}", request.subdomain());
        return company;
    }

    private String buildSchemaName(String subdomain) {
        String sanitized = subdomain.toLowerCase()
                .replaceAll("[^a-z0-9-]", "")
                .replace("-", "_");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Subdomain produces an invalid schema name");
        }
        String schemaName = SCHEMA_PREFIX + sanitized;
        if (!schemaName.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid schema name derived from subdomain");
        }
        return schemaName;
    }

    private void validateUnique(String subdomain, String emailDomain, String schemaName) {
        if (companyRepository.findBySubdomain(subdomain).isPresent()) {
            throw new IllegalArgumentException("Subdomain already exists");
        }
        if (companyRepository.findByEmailDomain(emailDomain).isPresent()) {
            throw new IllegalArgumentException("Email domain already exists");
        }
        if (companyRepository.findBySchemaName(schemaName).isPresent()) {
            throw new IllegalArgumentException("Schema name already exists");
        }
    }

    private Company createCompany(CompanyRegisterRequest request, String schemaName) {
        Company company = new Company();
        company.setName(request.companyName());
        company.setSubdomain(request.subdomain());
        company.setEmailDomain(request.emailDomain());
        company.setSchemaName(schemaName);
        company.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(company);
    }

    private void createAdminUser(String schemaName, CompanyRegisterRequest request) {
        try {
            TenantContext.setCurrentTenant(schemaName);

            User user = new User();
            user.setUsername(deriveUsername(request.adminEmail()));
            user.setEmail(request.adminEmail());
            user.setPassword(passwordEncoder.encode(request.adminPassword()));
            user.setEmailVerified(true);

            UserAccount account = new UserAccount();
            account.setUser(user);
            user.setUserAccount(account);

            UserProfile profile = new UserProfile();
            profile.setUser(user);
            profile.setFirstName(request.adminFirstName());
            profile.setLastName(request.adminLastName());
            user.setUserProfile(profile);

            userRepository.save(user);
            // Seed RBAC (permission catalog + Admin role) and grant Admin to the new
            // (role-less) user — runs in the tenant context set above. No-op in tests.
            rbacSeederProvider.ifAvailable(RbacSeeder::seedForCurrentTenant);
            log.info("Admin user created for tenant schema: {}", schemaName);
        } finally {
            TenantContext.clear();
        }
    }

    private String deriveUsername(String email) {
        int at = email.indexOf('@');
        String prefix = at > 0 ? email.substring(0, at) : email;
        if (prefix.length() > 70) {
            prefix = prefix.substring(0, 70);
        }
        return prefix;
    }

    private void createSchema(String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            log.info("Schema created: {}", schemaName);
        } catch (Exception e) {
            log.error("Error creating schema: {}", schemaName, e);
            throw new RuntimeException("Could not create schema: " + schemaName, e);
        }
    }

    private void runTenantMigrations(String schemaName) {
        tenantMigrationSupport.migrateSchema(schemaName);
    }
}
