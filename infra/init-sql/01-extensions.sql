-- ============================================================
-- Docker postgres image /docker-entrypoint-initdb.d/ script.
-- Runs ONCE on first DB creation only (when DATA directory is empty).
-- For versioned schema migrations use Flyway
-- (persistence/src/main/resources/db/migration/).
-- Files in this folder run in alphabetical order.
-- ============================================================

-- UUID generation: uuid_generate_v4() (some Hibernate fallbacks use it)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Case-insensitive text (emails, subdomains)
CREATE EXTENSION IF NOT EXISTS citext;

-- Trigram-based similarity (future: fuzzy search)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
