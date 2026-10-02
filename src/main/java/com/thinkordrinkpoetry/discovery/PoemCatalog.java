package com.thinkordrinkpoetry.discovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries behind the public discovery API and server-rendered pages.
 *
 * <p>Owned-poem and profile writes go through the JPA entities; public reading goes through the
 * {@code poet_listing} and {@code poem_listing} views, which hold the display rules (public name,
 * publication date, excerpt) in one place.
 */
@Repository
public class PoemCatalog {
    private static final int SEARCH_RESULT_LIMIT = 10;

    private static final RowMapper<PoetSummary> POET = (rs, row) -> new PoetSummary(
            rs.getObject("poet_id", UUID.class),
            rs.getString("display_name"),
            rs.getString("bio"),
            rs.getInt("poem_count"));

    private static final RowMapper<PoemSummary> POEM_SUMMARY = (rs, row) -> new PoemSummary(
            rs.getObject("poem_id", UUID.class),
            rs.getString("title"),
            rs.getString("excerpt"),
            publishedAt(rs));

    private static final RowMapper<RecentPoemSummary> RECENT_POEM = (rs, row) -> new RecentPoemSummary(
            rs.getObject("poem_id", UUID.class),
            rs.getObject("poet_id", UUID.class),
            rs.getString("title"),
            rs.getString("poet_display_name"),
            rs.getString("excerpt"),
            publishedAt(rs));

    private static final RowMapper<PoemDetail> POEM_DETAIL = (rs, row) -> new PoemDetail(
            rs.getObject("poem_id", UUID.class),
            rs.getObject("poet_id", UUID.class),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("poet_display_name"),
            rs.getString("poet_bio"),
            publishedAt(rs));

    private static final RowMapper<PoemSearchResult> POEM_SEARCH_RESULT = (rs, row) -> new PoemSearchResult(
            rs.getObject("poem_id", UUID.class),
            rs.getObject("poet_id", UUID.class),
            rs.getString("title"),
            rs.getString("poet_display_name"),
            rs.getString("excerpt"));

    private static final RowMapper<SitemapItem> SITEMAP_ITEM = (rs, row) -> new SitemapItem(
            rs.getObject(1, UUID.class),
            rs.getString(2),
            rs.getObject(3, LocalDate.class));

    private final JdbcTemplate jdbc;

    public PoemCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- Poets ----

    /** Any poet, including one without poems, so a new poet's own page can still load. */
    public Optional<PoetSummary> poet(UUID poetId) {
        return first(jdbc.query("select * from poet_listing where poet_id = ?", POET, poetId));
    }

    /** A poet as shown on public pages, which only exist once the poet has published. */
    public Optional<PoetSummary> publishedPoet(UUID poetId) {
        return poet(poetId).filter(poet -> poet.poemCount() > 0);
    }

    public Optional<PoetSummary> randomPublishedPoet() {
        return first(jdbc.query(
                "select * from poet_listing where poem_count > 0 order by random() limit 1", POET));
    }

    public List<PoetSummary> publishedPoets() {
        return jdbc.query("""
                select * from poet_listing where poem_count > 0
                order by lower(display_name), poet_id
                """, POET);
    }

    public int publishedPoetCount() {
        Integer count = jdbc.queryForObject("select count(*) from poet_listing where poem_count > 0", Integer.class);
        return count == null ? 0 : count;
    }

    public List<PoetSummary> searchPoets(String query) {
        String pattern = containsPattern(query);
        return jdbc.query("""
                select * from poet_listing
                where poem_count > 0 and (lower(full_name) like ? or lower(coalesce(pen_name, '')) like ?)
                order by display_name limit ?
                """, POET, pattern, pattern, SEARCH_RESULT_LIMIT);
    }

    // ---- Poems ----

    public Optional<PoemDetail> poem(UUID poemId) {
        return first(jdbc.query("select * from poem_listing where poem_id = ?", POEM_DETAIL, poemId));
    }

    /** A poet's poems, most recently edited first, for the reading sidebar. */
    public List<PoemSummary> poemsByPoet(UUID poetId) {
        return jdbc.query(
                "select * from poem_listing where poet_id = ? order by updated_at desc", POEM_SUMMARY, poetId);
    }

    /** A poet's poems, newest publication first, for their public page. */
    public List<PoemSummary> poemsByPoetByPublication(UUID poetId) {
        return jdbc.query(
                "select * from poem_listing where poet_id = ? order by published_at desc, title",
                POEM_SUMMARY, poetId);
    }

    /** The newest additions to the site, by when they were added. */
    public List<RecentPoemSummary> recentlyAdded(int limit, int offset) {
        return jdbc.query("""
                select * from poem_listing order by created_at desc, poem_id desc limit ? offset ?
                """, RECENT_POEM, limit, offset);
    }

    /** The most recently published poems, counting 1999 poems by their original date. */
    public List<RecentPoemSummary> recentlyPublished(int limit) {
        return jdbc.query(
                "select * from poem_listing order by published_at desc, poem_id desc limit ?", RECENT_POEM, limit);
    }

    public List<PoemSearchResult> searchPoems(String query) {
        String pattern = containsPattern(query);
        return jdbc.query("""
                select * from poem_listing
                where lower(title) like ? or lower(body) like ?
                order by updated_at desc limit ?
                """, POEM_SEARCH_RESULT, pattern, pattern, SEARCH_RESULT_LIMIT);
    }

    // ---- Sitemap ----

    public List<SitemapItem> poemsForSitemap() {
        return jdbc.query("select poem_id, title, updated_at::date from poem_listing", SITEMAP_ITEM);
    }

    /** Poets with poems; a poet page changes when the profile or any of their poems does. */
    public List<SitemapItem> poetsForSitemap() {
        return jdbc.query("""
                select l.poet_id, l.display_name, greatest(l.updated_at, max(po.updated_at))::date
                from poet_listing l join poem po on po.poet_id = l.poet_id
                group by l.poet_id, l.display_name, l.updated_at
                """, SITEMAP_ITEM);
    }

    public List<SitemapItem> poetBiosForSitemap() {
        return jdbc.query("""
                select poet_id, display_name, updated_at::date from poet_listing
                where poem_count > 0 and nullif(btrim(bio), '') is not null
                """, SITEMAP_ITEM);
    }

    /** A LIKE pattern matching the query anywhere. % and _ in the query are matched literally. */
    private static String containsPattern(String query) {
        String literal = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + literal + "%";
    }

    private static Instant publishedAt(ResultSet rs) throws SQLException {
        return rs.getTimestamp("published_at").toInstant();
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.stream().findFirst();
    }

    public record PoetSummary(UUID poetId, String displayName, String bio, int poemCount) {}

    public record PoemSummary(UUID poemId, String title, String excerpt, Instant createdAt) {}

    public record RecentPoemSummary(
            UUID poemId, UUID poetId, String title, String poetDisplayName, String excerpt, Instant createdAt) {}

    public record PoemDetail(
            UUID poemId, UUID poetId, String title, String poem, String poetDisplayName, String poetBio,
            Instant createdAt) {}

    public record PoemSearchResult(UUID poemId, UUID poetId, String title, String poetDisplayName, String excerpt) {}

    public record SitemapItem(UUID id, String name, LocalDate lastModified) {}
}
