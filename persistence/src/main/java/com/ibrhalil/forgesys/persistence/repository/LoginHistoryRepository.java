package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    Page<LoginHistory> findByUserId(UUID userId, Pageable pageable);

    Page<LoginHistory> findBySuccess(boolean success, Pageable pageable);

    Page<LoginHistory> findByUsername(String username, Pageable pageable);
}
