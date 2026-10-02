-- Poem search matches substrings with lower(poem) LIKE '%term%'. The full-text
-- index from V3 cannot serve that query, so every search read every poem.
-- A trigram index can, keeping search results exactly the same.
CREATE INDEX ix_poem_body_trgm ON poem USING gin (lower(poem) gin_trgm_ops);

-- Nothing queries the full-text index; it only slowed down poem writes.
DROP INDEX ix_poem_body_search;
