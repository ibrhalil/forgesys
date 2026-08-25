-- RISK-30: signup verification token hash-at-rest.
-- Backfill: every existing plain-text token is replaced by its SHA-256 hex digest
-- (matches TokenHasher.sha256Hex). Verify lookups hash the raw token presented in
-- the link, so outstanding (unexpired, unused) links keep working; consumed rows are
-- hashed too so a re-presented token still resolves to its row and reports
-- TENANT_TOKEN_ALREADY_USED rather than TENANT_TOKEN_INVALID.
UPDATE t_tenant_verification_tokens
SET token = encode(sha256(token::bytea), 'hex');

-- RISK-30: the pre-hashed admin password is only needed until phase 2 creates the
-- admin user. verifyAndProvision nulls the column afterwards — it must not stay
-- NOT NULL.
ALTER TABLE t_tenant_verification_tokens
    ALTER COLUMN admin_password_hash DROP NOT NULL;
