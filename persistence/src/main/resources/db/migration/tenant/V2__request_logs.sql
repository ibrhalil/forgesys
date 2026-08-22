CREATE TABLE t_request_logs (
    id UUID PRIMARY KEY,
    trace_id VARCHAR(100),
    method VARCHAR(10),
    path VARCHAR(500),
    status INTEGER,
    duration_ms BIGINT,
    user_id UUID,
    username VARCHAR(150),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_body JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_request_logs_trace_id ON t_request_logs(trace_id);
CREATE INDEX idx_request_logs_user_id ON t_request_logs(user_id);
CREATE INDEX idx_request_logs_created_at ON t_request_logs(created_at);
CREATE INDEX idx_request_logs_path ON t_request_logs(path);
CREATE INDEX idx_request_logs_status ON t_request_logs(status);