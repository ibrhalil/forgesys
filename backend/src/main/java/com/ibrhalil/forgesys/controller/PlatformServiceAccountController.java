package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountCreateRequest;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountCreatedResponse;
import com.ibrhalil.forgesys.dto.PlatformServiceAccountResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.service.PlatformServiceAccountService;
import com.ibrhalil.forgesys.web.SortGuard;
import com.ibrhalil.forgesys.web.filter.SearchQuery;
import com.ibrhalil.forgesys.web.filter.SearchRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** K-50 F5 service-account management — platform tokens only (scope == 'platform'). */
@RestController
@RequestMapping("/api/v1/platform/service-accounts")
@RequiredArgsConstructor
public class PlatformServiceAccountController {

    private static final String PLATFORM_MANAGE =
            "hasAuthority('platform:service-account:manage') and authentication.principal.scope == 'platform'";

    private final PlatformServiceAccountService platformServiceAccountService;

    @PostMapping
    @PreAuthorize(PLATFORM_MANAGE)
    public ResponseEntity<PlatformServiceAccountCreatedResponse> create(
            @Valid @RequestBody PlatformServiceAccountCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PlatformServiceAccountCreatedResponse body =
                platformServiceAccountService.create(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @PreAuthorize(PLATFORM_MANAGE)
    public ResponseEntity<PageResponse<PlatformServiceAccountResponse>> list(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            SearchQuery searchQuery) {
        if (searchQuery.present()) {
            // No searchable/filterable registration exists on this surface — sq carries
            // paging + sort only; q/filters would silently no-op, so they fail loud.
            com.ibrhalil.forgesys.dto.SearchRequest request = searchQuery.request();
            if (request.q() != null || (request.qFields() != null && !request.qFields().isEmpty())
                    || (request.filters() != null && !request.filters().isEmpty())) {
                throw new com.ibrhalil.forgesys.exception.SearchQueryDecodingException(
                        "Search query carries q/filters unsupported on this endpoint");
            }
            Pageable sqPageable = SearchRequests.toPageable(request, PlatformServiceAccountService.FILTER_FIELDS,
                    Sort.by(Sort.Direction.DESC, "createdDate"));
            return ResponseEntity.ok(PageResponse.of(platformServiceAccountService.list(sqPageable)));
        }
        SortGuard.require(pageable, PlatformServiceAccountService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(platformServiceAccountService.list(pageable)));
    }

    /** Revokes the key + disables the account — 204; the raw key is gone forever. */
    @DeleteMapping("/{id}")
    @PreAuthorize(PLATFORM_MANAGE)
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        platformServiceAccountService.revoke(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
