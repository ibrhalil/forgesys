package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.security.SessionRevocationService;
import com.ibrhalil.forgesys.security.TokenBlacklistService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import com.ibrhalil.forgesys.security.refresh.IssuedRefresh;
import com.ibrhalil.forgesys.security.refresh.RefreshSession;
import com.ibrhalil.forgesys.security.refresh.RefreshTokenStore;
import com.ibrhalil.forgesys.security.refresh.RotationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String HASH = "$2a$12$hashed";

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginHistoryService loginHistoryService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private SessionRevocationService sessionRevocationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(passwordEncoder, tokenProvider, userRepository,
                loginHistoryService, refreshTokenStore, userDetailsService, tokenBlacklistService,
                sessionRevocationService);
        TenantContext.setCurrentTenant("tenant_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void loginSuccessRecordsSuccessAndReturnsToken() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 0);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", HASH)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASH)).thenReturn(false);
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("tok");
        when(tokenProvider.getAccessTokenTtlMinutes()).thenReturn(5L);
        when(refreshTokenStore.issue(any(), any(), any(), any(), any())).thenReturn(
                new IssuedRefresh("refresh", new RefreshSession(userId, EMAIL, "tenant_test", null)));

        LoginResponse response = authService.login(new LoginRequest(EMAIL, "secret"));

        assertEquals("tok", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals(userId, response.userId());
        verify(loginHistoryService).record(userId, EMAIL, true, null);
    }

    @Test
    void loginUnknownEmailRecordsFailureWithNullUserId() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("$2a$12$dummy");

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest("ghost@example.com", "secret")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        verify(loginHistoryService).record(null, "ghost@example.com", false, "auth_bad_credentials");
    }

    @Test
    void loginAccountLessUserRecordsFailure() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail(EMAIL);
        user.setPassword(HASH);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "secret")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_bad_credentials");
    }

    @Test
    void loginWrongPasswordRecordsFailureAndIncrementsCounter() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "wrong")));

        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, ex.errorCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getUserAccount().getFailedLoginAttempts());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_bad_credentials");
    }

    @Test
    void loginLockoutStampsTokenInvalidBefore() {
        // 4 prior failed attempts → the 5th wrong password locks the account.
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.login(new LoginRequest(EMAIL, "wrong")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        UserAccount saved = captor.getValue().getUserAccount();
        assertEquals(5, saved.getFailedLoginAttempts());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getLockedUntil(), "account locked");
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getTokenInvalidBefore(),
                "Faz 1: lockout stamps tokenInvalidBefore so the locked account's access tokens die immediately");
    }

    @Test
    void loginLockedAccountRecordsFailureWithLockedReason() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, OffsetDateTime.now().plusMinutes(10), 5);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, "any")));

        assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, ex.errorCode());
        verify(loginHistoryService).record(userId, EMAIL, false, "auth_account_locked");
        // [RISK-22] a locked account never reaches the password compare (no timing/attempt leak)
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void refreshRotatesAndMintsNewAccessTokenWithFreshAuthorities() {
        UUID userId = UUID.randomUUID();
        String oldRefresh = "old-opaque-refresh";
        String newRefresh = "new-opaque-refresh";
        when(refreshTokenStore.rotate(oldRefresh)).thenReturn(new RotationResult.Rotated(
                new IssuedRefresh(newRefresh, new RefreshSession(userId, EMAIL, "tenant_test", null))));
        Set<GrantedAuthority> authorities = Set.<GrantedAuthority>of(new SimpleGrantedAuthority("tasks:task:read"));
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(
                new CustomUserDetails(userId, EMAIL, null, true, true, true, true, authorities, "tenant_test", null));
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("newAccess");
        when(tokenProvider.getAccessTokenTtlMinutes()).thenReturn(15L);

        LoginResponse response = authService.refresh(oldRefresh);

        assertEquals("newAccess", response.accessToken());
        assertEquals(newRefresh, response.refreshToken());
        verify(loginHistoryService).record(eq(userId), eq(EMAIL), eq(true), eq(null));
    }

    @Test
    void refreshReuseRevokesAllSessionsAndInvalidatesAccessTokens() {
        UUID userId = UUID.randomUUID();
        User user = userWithAccount(userId, null, 0);
        when(refreshTokenStore.rotate("leaked")).thenReturn(
                new RotationResult.ReuseDetected(userId, "tenant_test"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh("leaked"));

        assertEquals(ErrorCode.AUTH_REFRESH_TOKEN_REUSE, ex.errorCode());
        verify(refreshTokenStore).revokeAllForUser(userId, "tenant_test");
        // tokenInvalidBefore stamped (kills outstanding access tokens)
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserAccount().getTokenInvalidBefore()).isNotNull();
    }

    @Test
    void refreshUnknownTokenThrowsInvalid() {
        when(refreshTokenStore.rotate("bogus")).thenReturn(new RotationResult.Unknown());

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh("bogus"));

        assertEquals(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, ex.errorCode());
        verify(refreshTokenStore, never()).revokeAllForUser(any(), any());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void refreshBlankTokenThrowsInvalid() {
        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh("  "));
        assertEquals(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, ex.errorCode());
        verify(refreshTokenStore, never()).rotate(any());
    }

    @Test
    void refreshCrossTenantTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenStore.rotate("t")).thenReturn(new RotationResult.Rotated(
                new IssuedRefresh("n", new RefreshSession(userId, EMAIL, "other_tenant", null))));

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh("t"));

        assertEquals(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, ex.errorCode());
        verify(refreshTokenStore).revoke("n");
        verifyNoInteractions(userDetailsService);
    }

    private User userWithAccount(UUID id, OffsetDateTime lockedUntil, int failedAttempts) {
        User user = new User();
        user.setId(id);
        user.setEmail(EMAIL);
        user.setUsername("user");
        user.setPassword(HASH);
        UserAccount account = new UserAccount();
        account.setLockedUntil(lockedUntil);
        account.setFailedLoginAttempts(failedAttempts);
        user.setUserAccount(account);
        return user;
    }
}
