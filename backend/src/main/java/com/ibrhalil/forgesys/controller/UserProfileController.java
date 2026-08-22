package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.MeResponse;
import com.ibrhalil.forgesys.dto.PasswordChangeRequest;
import com.ibrhalil.forgesys.dto.UserProfileUpdateRequest;
import com.ibrhalil.forgesys.dto.UserResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service endpoints for the authenticated user's own account. Tenant-scoped but
 * requiring no {@code iam:*} permission — any authenticated user may read/update their
 * own profile and change their own password. Mapped under {@code /api/v1/users/me};
 * the literal {@code me} segment takes precedence over the {@code /{id}} path variable
 * declared in {@link UserController}.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    /**
     * The single /me endpoint (K-37): full self view from the DB + the authorities
     * embedded in the caller's access token (claims — no extra resolution work).
     */
    @GetMapping
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        UserResponse user = userService.findById(principal.getUserId());
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(new MeResponse(
                user.id(), user.username(), user.email(), user.emailVerified(), user.enabled(),
                user.lockedUntil(), user.firstName(), user.lastName(), user.phoneNumber(),
                user.address(), user.city(), user.country(), user.zipCode(),
                user.roles(), user.groups(), authorities));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                                     @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.getUserId(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
