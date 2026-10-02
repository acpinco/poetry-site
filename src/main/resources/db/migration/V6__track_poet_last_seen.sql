-- Track when each poet last used the site on the poet itself, instead of on
-- short-lived sessions that are deleted after they expire.
ALTER TABLE poet ADD COLUMN last_seen_at timestamptz;

UPDATE poet
SET last_seen_at = activity.last_used_at
FROM (
    SELECT poet_id, max(last_used_at) AS last_used_at
    FROM user_session
    WHERE poet_id IS NOT NULL
    GROUP BY poet_id
) activity
WHERE activity.poet_id = poet.id;

CREATE INDEX ix_poet_last_seen_at ON poet (last_seen_at DESC NULLS LAST);

ALTER TABLE user_session DROP COLUMN last_used_at;

-- Recording a visit is not a profile change: only bump updated_at when some
-- other column changes. updated_at feeds the sitemap's lastmod dates.
-- full_name is excluded because generated columns are not yet computed in
-- BEFORE triggers; it changes only with first_name/last_name anyway.
CREATE FUNCTION set_poet_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (to_jsonb(NEW) - 'last_seen_at' - 'updated_at' - 'full_name')
            IS DISTINCT FROM (to_jsonb(OLD) - 'last_seen_at' - 'updated_at' - 'full_name') THEN
        NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_poet_updated_at ON poet;

CREATE TRIGGER trg_poet_updated_at
BEFORE UPDATE ON poet
FOR EACH ROW
EXECUTE FUNCTION set_poet_updated_at();
