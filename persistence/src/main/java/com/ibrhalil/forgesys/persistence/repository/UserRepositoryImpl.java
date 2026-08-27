package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<User> findByIdIncludingDeleted(UUID id) {
        return SoftDeleteFilterScope.includingDeleted(entityManager,
                () -> entityManager.createQuery("select u from User u where u.id = :id", User.class)
                        .setParameter("id", id)
                        .getResultStream()
                        .findFirst());
    }

    @Override
    public List<User> findAllIncludingDeleted() {
        return SoftDeleteFilterScope.includingDeleted(entityManager,
                () -> entityManager.createQuery("select u from User u order by u.createdDate", User.class)
                        .getResultList());
    }
}
