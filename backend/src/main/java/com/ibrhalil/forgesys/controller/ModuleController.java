package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.ModuleResponse;
import com.ibrhalil.forgesys.service.ModuleActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module catalog + activation endpoints (K-16 / Epic 3.0.A). Tenant-scoped: the company
 * is resolved from the {@code TenantFilter}-set context.
 */
@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleActivationService moduleActivationService;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:module:read')")
    public List<ModuleResponse> listModules() {
        return moduleActivationService.listModules();
    }

    @PostMapping("/{key}/activate")
    @PreAuthorize("hasAuthority('iam:module:write')")
    public ModuleResponse activate(@PathVariable String key) {
        return moduleActivationService.activate(key);
    }
}
