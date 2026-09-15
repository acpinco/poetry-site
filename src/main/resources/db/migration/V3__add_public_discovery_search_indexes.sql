CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_poet_full_name_trgm ON poet USING gin (lower(full_name) gin_trgm_ops);
CREATE INDEX ix_poet_pen_name_trgm ON poet USING gin (lower(coalesce(pen_name, '')) gin_trgm_ops);
CREATE INDEX ix_poem_title_trgm ON poem USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_poem_body_search ON poem USING gin (to_tsvector('english', poem));
