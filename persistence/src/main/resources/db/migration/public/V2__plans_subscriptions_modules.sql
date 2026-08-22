CREATE TABLE t_plans (
    id UUID PRIMARY KEY,
    plan_key VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    plan_rank INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE UNIQUE INDEX uk_plans_key ON t_plans(plan_key);
CREATE TABLE t_subscriptions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_subscriptions_company
        FOREIGN KEY (company_id) REFERENCES t_companies(id),
    CONSTRAINT fk_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES t_plans(id)
);
CREATE INDEX idx_subscriptions_company ON t_subscriptions(company_id);
CREATE UNIQUE INDEX uk_subscriptions_company
    ON t_subscriptions(company_id) WHERE is_deleted = false;
CREATE TABLE t_tenant_modules (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    module_key VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_tenant_modules_company
        FOREIGN KEY (company_id) REFERENCES t_companies(id)
);
CREATE INDEX idx_tenant_modules_company ON t_tenant_modules(company_id);
CREATE UNIQUE INDEX uk_tenant_modules_company_module
    ON t_tenant_modules(company_id, module_key) WHERE is_deleted = false;
