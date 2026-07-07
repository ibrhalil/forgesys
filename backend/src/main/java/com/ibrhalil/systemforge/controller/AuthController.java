package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.dto.CompanyRegisterRequest;
import com.ibrhalil.systemforge.entity.Company;
import com.ibrhalil.systemforge.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantProvisioningService tenantProvisioningService;

    @PostMapping("/company/register")
    public ResponseEntity<Map<String, Object>> registerCompany(@Valid @RequestBody CompanyRegisterRequest request) {
        Company company = tenantProvisioningService.provisionTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", company.getId(),
                "name", company.getName(),
                "subdomain", company.getSubdomain(),
                "schemaName", company.getSchemaName()
        ));
    }
}
