-- Read model for the public discovery API and server-rendered pages. The display
-- rules live here once instead of being repeated in every query:
--   * a poet's public name is their pen name, falling back to their full name;
--   * a poem's publication time is its 1999 submission date when it has one.
-- Views are inlined by the planner, so filters on these columns still use the
-- underlying indexes and unselected columns (like excerpt) cost nothing.

CREATE VIEW poet_listing AS
SELECT p.id AS poet_id,
       coalesce(nullif(p.pen_name, ''), p.full_name) AS display_name,
       p.full_name,
       p.pen_name,
       p.bio,
       p.updated_at,
       (SELECT count(*) FROM poem po WHERE po.poet_id = p.id)::integer AS poem_count
FROM poet p;

CREATE VIEW poem_listing AS
SELECT po.id AS poem_id,
       po.poet_id,
       po.title,
       po.poem AS body,
       left(regexp_replace(po.poem, '\s+', ' ', 'g'), 160) AS excerpt,
       coalesce(po.legacy_submitted_on::timestamp AT TIME ZONE 'UTC', po.created_at) AS published_at,
       po.created_at,
       po.updated_at,
       coalesce(nullif(p.pen_name, ''), p.full_name) AS poet_display_name,
       p.bio AS poet_bio
FROM poem po
JOIN poet p ON p.id = po.poet_id;
