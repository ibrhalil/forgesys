package com.ibrhalil.systemforge.persistence.repository;

import com.ibrhalil.systemforge.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findBySubdomain(String subdomain);

    Optional<Company> findByEmailDomain(String emailDomain);

    Optional<Company> findBySchemaName(String schemaName);
}
