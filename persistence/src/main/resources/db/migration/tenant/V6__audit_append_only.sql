-- Faz 2a: append-only audit tables. t_audit_logs and t_login_history are the IAM
-- evidence trail ("who did what to which entity" / "who tried to log in") and MUST be
-- tamper-proof: a compromised admin or app-level SQL must not be able to rewrite or
-- delete history. Both tables are insert-only by design (AuditService / LoginHistoryService
-- only ever insert; no UPDATE/DELETE path exists in the app), so blocking UPDATE/DELETE at
-- the DB layer is safe and breaks nothing.
--
-- A single shared trigger function raises on UPDATE or DELETE; a BEFORE row trigger on
-- each table invokes it. The app's DB role (not a superuser) is bound by it; only a real
-- superuser could bypass (out of scope — the threat model is app/admin compromise, and a
-- separate audit-writer role is the stronger K-27 follow-up). ALTER TABLE is NOT a row
-- DML, so future migrations can still evolve the schema.
--
-- Function + triggers are created in the tenant schema (TenantMigrationSupport sets
-- search_path to the tenant schema before running this), so each tenant owns its own copy
-- with no cross-tenant name collision.

CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit tables are append-only: % not permitted on %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_audit_logs_immutable ON t_audit_logs;
CREATE TRIGGER tr_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON t_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

DROP TRIGGER IF EXISTS tr_login_history_immutable ON t_login_history;
CREATE TRIGGER tr_login_history_immutable
    BEFORE UPDATE OR DELETE ON t_login_history
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
