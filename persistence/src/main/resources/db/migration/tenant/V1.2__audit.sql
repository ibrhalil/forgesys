CREATE TABLE t_audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_name VARCHAR(200) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    entity_name VARCHAR(200),
    old_value JSONB,
    new_value JSONB,
    request_body JSONB,
    ip_address VARCHAR(45),
    trace_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_audit_logs_actor_id ON t_audit_logs(actor_id);
CREATE INDEX idx_audit_logs_entity ON t_audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON t_audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON t_audit_logs(created_at);
CREATE TABLE t_login_history (
    id UUID PRIMARY KEY,
    user_id UUID,
    username VARCHAR(150) NOT NULL,
    success BOOLEAN NOT NULL,
    reason VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_login_history_user_id ON t_login_history(user_id);
CREATE INDEX idx_login_history_created_at ON t_login_history(created_at);
CREATE INDEX idx_login_history_success ON t_login_history(success);
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit tables are append-only: % not permitted on %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON t_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
CREATE TRIGGER tr_login_history_immutable
    BEFORE UPDATE OR DELETE ON t_login_history
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
