CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE poet (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    email varchar(320) NOT NULL,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    full_name varchar(201) GENERATED ALWAYS AS (
        btrim(first_name || ' ' || last_name)
    ) STORED,
    pen_name varchar(100),
    bio text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_poet_email_canonical CHECK (
        email = lower(btrim(email))
        AND char_length(email) BETWEEN 3 AND 320
    ),
    CONSTRAINT ck_poet_first_name_valid CHECK (
        first_name = btrim(first_name)
        AND char_length(first_name) BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_poet_last_name_valid CHECK (
        last_name = btrim(last_name)
        AND char_length(last_name) BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_poet_pen_name_valid CHECK (
        pen_name IS NULL OR (
            pen_name = btrim(pen_name)
            AND char_length(pen_name) BETWEEN 1 AND 100
        )
    ),
    CONSTRAINT ck_poet_bio_length CHECK (
        bio IS NULL OR char_length(bio) <= 10000
    )
);

CREATE UNIQUE INDEX uq_poet_email_ci
    ON poet (lower(email));

CREATE UNIQUE INDEX uq_poet_pen_name_ci
    ON poet (lower(pen_name))
    WHERE pen_name IS NOT NULL;

CREATE INDEX ix_poet_full_name_prefix
    ON poet (lower(full_name) text_pattern_ops);

CREATE TABLE email_login_token (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    email varchar(320) NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_email_login_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_login_token_email_canonical CHECK (
        email = lower(btrim(email))
        AND char_length(email) BETWEEN 3 AND 320
    ),
    CONSTRAINT ck_email_login_token_hash_format CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_email_login_token_expiry CHECK (
        expires_at > created_at
    )
);

CREATE INDEX ix_email_login_token_expiry
    ON email_login_token (expires_at);

CREATE TABLE user_session (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    session_token_hash varchar(64) NOT NULL,
    authenticated_email varchar(320) NOT NULL,
    poet_id uuid REFERENCES poet(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    last_used_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_session_token_hash UNIQUE (session_token_hash),
    CONSTRAINT ck_user_session_email_canonical CHECK (
        authenticated_email = lower(btrim(authenticated_email))
        AND char_length(authenticated_email) BETWEEN 3 AND 320
    ),
    CONSTRAINT ck_user_session_token_hash_format CHECK (
        session_token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_user_session_expiry CHECK (
        expires_at > created_at
    )
);

CREATE INDEX ix_user_session_expiry
    ON user_session (expires_at);

CREATE TRIGGER trg_poet_updated_at
BEFORE UPDATE ON poet
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_email_login_token_updated_at
BEFORE UPDATE ON email_login_token
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_session_updated_at
BEFORE UPDATE ON user_session
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
