package com.ibrhalil.forgesys.exception;

/**
 * Authentication failures (bad credentials, invalid/expired/revoked token, etc.).
 * Use the {@code ErrorCode}-based constructor to pick the precise failure code;
 * convenience factories cover the most common cases.
 */
public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static AuthException badCredentials() {
        return new AuthException(ErrorCode.AUTH_BAD_CREDENTIALS);
    }

    public static AuthException accountLocked() {
        return new AuthException(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }
}
