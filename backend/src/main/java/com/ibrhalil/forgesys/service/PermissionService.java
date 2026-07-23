package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.PermissionResponse;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllByOrderByNameAsc().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(), permission.getName(), permission.getDescription()))
                .toList();
    }
}
