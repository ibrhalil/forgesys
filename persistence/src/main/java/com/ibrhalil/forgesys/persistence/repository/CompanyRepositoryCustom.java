package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Company;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Opt-in "include soft-deleted" reads (EGH item 4 prototype — Company + User only). */
public interface CompanyRepositoryCustom {

    Optional<Company> findByIdIncludingDeleted(UUID id);

    List<Company> findAllIncludingDeleted();
}
