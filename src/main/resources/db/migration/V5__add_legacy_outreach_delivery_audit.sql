CREATE TABLE legacy_poetry_outreach_delivery (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    campaign_id varchar(100) NOT NULL,
    poet_id uuid NOT NULL REFERENCES poet(id) ON DELETE CASCADE,
    historical_email varchar(320) NOT NULL,
    delivery_email varchar(320) NOT NULL,
    delivery_mode varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    public_url text NOT NULL,
    sent_at timestamptz,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_legacy_outreach_delivery_mode CHECK (delivery_mode IN ('TEST', 'LIVE')),
    CONSTRAINT ck_legacy_outreach_delivery_status CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT ux_legacy_outreach_delivery UNIQUE (campaign_id, poet_id, delivery_email)
);

CREATE INDEX ix_legacy_outreach_delivery_campaign_status
    ON legacy_poetry_outreach_delivery (campaign_id, status);

CREATE TRIGGER trg_legacy_outreach_delivery_updated_at
    BEFORE UPDATE ON legacy_poetry_outreach_delivery
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
