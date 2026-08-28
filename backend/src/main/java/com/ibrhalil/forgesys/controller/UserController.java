package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AdminPasswordResetRequest;
import com.ibrhalil.forgesys.dto.AssignGroupsRequest;
import com.ibrhalil.forgesys.dto.AssignRolesRequest;
import com.ibrhalil.forgesys.dto.PageResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.UserActivityResponse;
import com.ibrhalil.forgesys.dto.UserCreateRequest;
import com.ibrhalil.forgesys.dto.UserDirectoryViewResponse;
import com.ibrhalil.forgesys.dto.UserResponse;
import com.ibrhalil.forgesys.dto.UserUpdateRequest;
import com.ibrhalil.forgesys.service.UserService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Read authority: full tenant visibility ({@code iam:user:read}) or own-groups scope ({@code iam:group-member:read}). */
    private static final String READ_USERS = "hasAnyAuthority('iam:user:read', 'iam:group-member:read')";

    @GetMapping
    @PreAuthorize(READ_USERS)
    public ResponseEntity<PageResponse<UserDirectoryViewResponse>> list(
            @PageableDefault(sort = "email") Pageable pageable,
            SearchQuery searchQuery,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> qFields) {
        if (searchQuery.present()) {
            SearchRequest request = searchQuery.request();
            Pageable sqPageable = SearchRequests.toPageable(request, UserService.FILTER_FIELDS, Sort.by("email"));
            return ResponseEntity.ok(PageResponse.of(userService.search(request, sqPageable)));
        }
        SortGuard.require(pageable, UserService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(userService.search(q, qFields, pageable)));
    }

    /** Filter-engine variant of the list: paging + multi-sort + filters + {@code q} in one POST body. */
    @PostMapping("/search")
    @PreAuthorize(READ_USERS)
    public ResponseEntity<PageResponse<UserDirectoryViewResponse>> search(@Valid @RequestBody SearchRequest request) {
        Pageable pageable = SearchRequests.toPageable(request, UserService.FILTER_FIELDS);
        return ResponseEntity.ok(PageResponse.of(userService.search(request, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_USERS)
    public ResponseEntity<UserResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping("/{id}/effective-permissions")
    @PreAuthorize(READ_USERS)
    public ResponseEntity<List<String>> effectivePermissions(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.effectivePermissions(id));
    }

    /** Temporal activity summary (creation/update stamps, last login, last failed login). */
    @GetMapping("/{id}/activity")
    @PreAuthorize(READ_USERS)
    public ResponseEntity<UserActivityResponse> activity(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.activity(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:user:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Clears an active brute-force lockout ahead of expiry ([RISK-22]); an action, not a deletion — POST (K-37). */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<Void> unlock(@PathVariable UUID id) {
        userService.unlock(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-sends the verification mail; 409 {@code user_already_verified} when already verified. */
    @PostMapping("/{id}/resend-verification")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<Void> resendVerification(@PathVariable UUID id) {
        userService.resendVerification(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<UserResponse> setRoles(@PathVariable UUID id,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ResponseEntity.ok(userService.setRoles(id, request));
    }

    @PutMapping("/{id}/groups")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<UserResponse> setGroups(@PathVariable UUID id,
                                                  @Valid @RequestBody AssignGroupsRequest request) {
        return ResponseEntity.ok(userService.setGroups(id, request));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @Valid @RequestBody AdminPasswordResetRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
