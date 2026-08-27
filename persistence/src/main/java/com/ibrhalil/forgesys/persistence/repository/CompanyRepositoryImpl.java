package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.Company;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CompanyRepositoryImpl implements CompanyRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Company> findByIdIncludingDeleted(UUID id) {
        return SoftDeleteFilterScope.includingDeleted(entityManager,
                () -> entityManager.createQuery("select c from Company c where c.id = :id", Company.class)
                        .setParameter("id", id)
                        .getResultStream()
                        .findFirst());
    }

    @Override
    public List<Company> findAllIncludingDeleted() {
        return SoftDeleteFilterScope.includingDeleted(entityManager,
                () -> entityManager.createQuery("select c from Company c order by c.createdDate", Company.class)
                        .getResultList());
    }
}
