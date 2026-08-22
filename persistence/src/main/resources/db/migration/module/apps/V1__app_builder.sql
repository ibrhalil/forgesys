-- Custom App Builder (K-15 / Epic 3.0.B) — module-scoped tenant tables.
-- Runs via TenantMigrationSupport.migrateModule against the per-module history table
-- flyway_schema_history_mod_apps (baseline 0), OUTSIDE db/migration/tenant (K-16:
-- recursive location scan would swallow module versions into the core history).
-- PostgreSQL-only (jsonb + GIN + partial unique indexes); never executes on H2.

CREATE TABLE t_apps (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    icon VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_apps_name ON t_apps(name);
CREATE UNIQUE INDEX uk_apps_name ON t_apps(name) WHERE is_deleted = false;

CREATE TABLE t_app_properties (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    prop_type VARCHAR(20) NOT NULL,
    config JSONB,
    required BOOLEAN NOT NULL DEFAULT false,
    position INT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_app_properties_app
        FOREIGN KEY (app_id) REFERENCES t_apps(id) ON DELETE CASCADE
);
CREATE INDEX idx_app_properties_app ON t_app_properties(app_id);
CREATE UNIQUE INDEX uk_app_properties_name
    ON t_app_properties(app_id, name) WHERE is_deleted = false;

CREATE TABLE t_app_records (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_app_records_app
        FOREIGN KEY (app_id) REFERENCES t_apps(id) ON DELETE CASCADE
);
CREATE INDEX idx_app_records_app ON t_app_records(app_id);

-- EAV value rows: dependent data of a record (no soft-delete — clearing a value
-- removes the row; re-setting inserts again). Plain UNIQUE, no partial index.
CREATE TABLE t_app_record_values (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL,
    property_id UUID NOT NULL,
    value JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_app_record_values_record
        FOREIGN KEY (record_id) REFERENCES t_app_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_record_values_property
        FOREIGN KEY (property_id) REFERENCES t_app_properties(id),
    CONSTRAINT uk_app_record_values_record_property UNIQUE (record_id, property_id)
);
CREATE INDEX idx_app_record_values_record ON t_app_record_values(record_id);
CREATE INDEX idx_app_record_values_property ON t_app_record_values(property_id);
-- GIN index backing the JSONB containment/equality filters of record search.
CREATE INDEX idx_app_record_values_value ON t_app_record_values USING gin (value jsonb_path_ops);

CREATE TABLE t_app_views (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    view_type VARCHAR(20) NOT NULL,
    config JSONB,
    position INT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_app_views_app
        FOREIGN KEY (app_id) REFERENCES t_apps(id) ON DELETE CASCADE
);
CREATE INDEX idx_app_views_app ON t_app_views(app_id);
CREATE UNIQUE INDEX uk_app_views_name
    ON t_app_views(app_id, name) WHERE is_deleted = false;
