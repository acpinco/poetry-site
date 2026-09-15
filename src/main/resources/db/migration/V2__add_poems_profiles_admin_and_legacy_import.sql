ALTER TABLE poet
    ADD COLUMN legacy_submitted_on date,
    ADD COLUMN account_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN role varchar(32) NOT NULL DEFAULT 'USER',
    ADD COLUMN locked_at timestamptz,
    ADD COLUMN locked_reason text,
    ADD CONSTRAINT ck_poet_account_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'LEGACY_UNCLAIMED')),
    ADD CONSTRAINT ck_poet_role CHECK (role IN ('USER', 'ADMIN'));

CREATE TABLE poem (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    poet_id uuid NOT NULL REFERENCES poet(id) ON DELETE CASCADE,
    title varchar(200) NOT NULL,
    poem text NOT NULL,
    legacy_submitted_on date,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_poem_title CHECK (title = btrim(title) AND char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT ck_poem_body CHECK (char_length(btrim(poem)) BETWEEN 1 AND 100000)
);
CREATE INDEX ix_poem_poet_updated ON poem (poet_id, updated_at DESC);
CREATE TRIGGER trg_poem_updated_at BEFORE UPDATE ON poem FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE admin_audit_event (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    actor_poet_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    target_type varchar(32) NOT NULL,
    target_id uuid NOT NULL,
    reason text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_admin_audit_event_updated_at BEFORE UPDATE ON admin_audit_event FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE legacy_poet_import_map (
    legacy_poet_id bigint PRIMARY KEY,
    poet_id uuid NOT NULL UNIQUE REFERENCES poet(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_legacy_poet_import_map_updated_at BEFORE UPDATE ON legacy_poet_import_map FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE legacy_poem_import_map (
    legacy_poem_id bigint PRIMARY KEY,
    poem_id uuid NOT NULL UNIQUE REFERENCES poem(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_legacy_poem_import_map_updated_at BEFORE UPDATE ON legacy_poem_import_map FOR EACH ROW EXECUTE FUNCTION set_updated_at();
