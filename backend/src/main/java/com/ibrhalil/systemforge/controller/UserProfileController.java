package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.dto.PasswordChangeRequest;
import com.ibrhalil.systemforge.dto.UserProfileUpdateRequest;
import com.ibrhalil.systemforge.dto.UserResponse;
import com.ibrhalil.systemforge.security.CustomUserDetails;
import com.ibrhalil.systemforge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.findById(principal.getUserId()));
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
