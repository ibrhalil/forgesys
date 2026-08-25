package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.UserAuthTokenRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * User lifecycle token semantics ([RISK-30]/[RISK-25] conventions): hash-at-rest issue,
 * supersede-on-reissue, atomic claim consumption and the error-code mapping.
 */
@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserAuthTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;

    private UserTokenService service;

    @BeforeEach
    void setUp() {
        service = new UserTokenService(tokenRepository, userRepository);
        ReflectionTestUtils.setField(service, "emailVerifyTtlHours", 24L);
        ReflectionTestUtils.setField(service, "resetTokenTtlMinutes", 30L);
    }

    // --- issue -----------------------------------------------------------

    @Test
    void issue_persistsOnlyTheDigestAndReturnsTheRawToken() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        String raw = service.issue(USER_ID, UserAuthTokenPurpose.EMAIL_VERIFY);

        assertThat(raw).isNotBlank();
        ArgumentCaptor<UserAuthToken> captor = ArgumentCaptor.forClass(UserAuthToken.class);
        verify(tokenRepository).save(captor.capture());
        UserAuthToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getPurpose()).isEqualTo(UserAuthTokenPurpose.EMAIL_VERIFY);
        assertThat(saved.getExpiresAt())
                .isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23));
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    void issue_supersedesOutstandingTokensOfSamePurpose() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        service.issue(USER_ID, UserAuthTokenPurpose.PASSWORD_RESET);

        verify(tokenRepository).invalidateOutstanding(eq(USER_ID),
                eq(UserAuthTokenPurpose.PASSWORD_RESET), any(OffsetDateTime.class));
    }

    @Test
    void issue_passwordResetTtlIsMinutes() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());

        service.issue(USER_ID, UserAuthTokenPurpose.PASSWORD_RESET);

        ArgumentCaptor<UserAuthToken> captor = ArgumentCaptor.forClass(UserAuthToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    // --- consume ---------------------------------------------------------

    @Test
    void consume_validTokenClaimsAndReturnsEntity() {
        UserAuthToken token = validToken(UserAuthTokenPurpose.EMAIL_VERIFY);
        when(tokenRepository.findByTokenHash(TokenHasher.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(tokenRepository.claimToken(eq(TokenHasher.sha256Hex("raw")), any(OffsetDateTime.class))).thenReturn(1);

        UserAuthToken consumed = service.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY);

        assertThat(consumed).isSameAs(token);
        assertThat(consumed.getUsedAt()).isNotNull();
    }

    @Test
    void consume_unknownTokenThrowsInvalid() {
        when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("nope", UserAuthTokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_INVALID);

        verify(tokenRepository, never()).claimToken(any(), any());
    }

    @Test
    void consume_usedTokenThrowsAlreadyUsed() {
        UserAuthToken token = validToken(UserAuthTokenPurpose.EMAIL_VERIFY);
        token.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_ALREADY_USED);
    }

    @Test
    void consume_expiredTokenThrowsExpired() {
        UserAuthToken token = validToken(UserAuthTokenPurpose.EMAIL_VERIFY);
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_EXPIRED);
    }

    @Test
    void consume_crossPurposeTokenThrowsInvalid() {
        UserAuthToken token = validToken(UserAuthTokenPurpose.EMAIL_VERIFY);
        when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw", UserAuthTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_INVALID);

        verify(tokenRepository, never()).claimToken(any(), any());
    }

    @Test
    void consume_concurrentClaimLostThrowsAlreadyUsed() {
        UserAuthToken token = validToken(UserAuthTokenPurpose.EMAIL_VERIFY);
        when(tokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(token));
        when(tokenRepository.claimToken(any(), any(OffsetDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consume("raw", UserAuthTokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_TOKEN_ALREADY_USED);
    }

    // --- purge -----------------------------------------------------------

    @Test
    void purgeDelegatesToRepositoryWithCutoff() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        when(tokenRepository.purgeStale(cutoff)).thenReturn(2);

        int purged = service.purgeStaleForCurrentTenant(cutoff);

        assertThat(purged).isEqualTo(2);
    }

    // --- helpers ---------------------------------------------------------

    private UserAuthToken validToken(UserAuthTokenPurpose purpose) {
        UserAuthToken token = new UserAuthToken();
        token.setId(UUID.randomUUID());
        token.setPurpose(purpose);
        token.setTokenHash(TokenHasher.sha256Hex("raw"));
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        return token;
    }
}
