package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.dto.CompanyStatusUpdateRequest;
import com.ibrhalil.forgesys.service.PlatformCompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class PlatformCompanyController {

    private final PlatformCompanyService platformCompanyService;

    @GetMapping
    @PreAuthorize("hasAuthority('platform:company:read')")
    public List<CompanyResponse> findAll() {
        return platformCompanyService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:company:read')")
    public CompanyResponse findById(@PathVariable UUID id) {
        return platformCompanyService.findById(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('platform:company:write')")
    public CompanyResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyStatusUpdateRequest request) {
        return platformCompanyService.updateStatus(id, request.status());
    }
}
