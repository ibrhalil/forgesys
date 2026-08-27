package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Opt-in "include soft-deleted" reads (EGH item 4 prototype — Company + User only). */
public interface UserRepositoryCustom {

    Optional<User> findByIdIncludingDeleted(UUID id);

    List<User> findAllIncludingDeleted();
}
