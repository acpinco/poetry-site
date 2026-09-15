package com.thinkordrinkpoetry.discovery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private final JdbcTemplate jdbc;
    private final PublicDiscoveryRateLimiter limiter;

    public DiscoveryController(JdbcTemplate jdbc, PublicDiscoveryRateLimiter limiter) { this.jdbc = jdbc; this.limiter = limiter; }

    @GetMapping("/home")
    public HomeResponse home(HttpServletRequest request) {
        limiter.check(clientIp(request), false);
        PoetSummary poet = jdbc.query("""
                select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), p.bio, count(po.id)
                from poet p join poem po on po.poet_id = p.id
                group by p.id, p.pen_name, p.full_name, p.bio order by random() limit 1
                """, rs -> rs.next() ? new PoetSummary(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getInt(4)) : null);
        if (poet == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No poems are available yet.");
        List<PoemSummary> poems = poemsFor(poet.poetId());
        PoemSummary selected = poems.get((int) (Math.random() * poems.size()));
        return new HomeResponse(poet, poems, poem(selected.poemId()));
    }

    @GetMapping("/poets/{poetId}/poems")
    public PoetPoemsResponse poemsByPoet(@PathVariable UUID poetId, HttpServletRequest request) {
        limiter.check(clientIp(request), false);
        PoetSummary poet = poet(poetId);
        return new PoetPoemsResponse(poet, poemsFor(poetId));
    }

    @GetMapping("/poets")
    public List<PoetSummary> poets(HttpServletRequest request) {
        limiter.check(clientIp(request), false);
        return jdbc.query("""
                select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), p.bio, count(po.id)
                from poet p join poem po on po.poet_id = p.id
                group by p.id, p.pen_name, p.full_name, p.bio
                order by lower(coalesce(nullif(p.pen_name, ''), p.full_name)), p.id
                """, (rs, row) -> new PoetSummary(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getInt(4)));
    }

    @GetMapping("/poems/{poemId}")
    public PoemDetail poem(@PathVariable UUID poemId, HttpServletRequest request) { limiter.check(clientIp(request), false); return poem(poemId); }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam @Size(min = 2, max = 100) String q, HttpServletRequest request) {
        limiter.check(clientIp(request), true);
        String pattern = "%" + q.trim().toLowerCase() + "%";
        List<PoetSummary> poets = jdbc.query("""
                select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), p.bio, count(po.id)
                from poet p join poem po on po.poet_id = p.id
                where lower(p.full_name) like ? or lower(coalesce(p.pen_name, '')) like ?
                group by p.id, p.pen_name, p.full_name, p.bio order by 2 limit 10
                """, (rs, row) -> new PoetSummary(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getInt(4)), pattern, pattern);
        List<PoemSearchResult> poems = jdbc.query("""
                select po.id, po.poet_id, po.title, coalesce(nullif(p.pen_name, ''), p.full_name), left(regexp_replace(po.poem, '\\s+', ' ', 'g'), 160)
                from poem po join poet p on p.id = po.poet_id
                where lower(po.title) like ? or lower(po.poem) like ? order by po.updated_at desc limit 10
                """, (rs, row) -> new PoemSearchResult(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getString(5)), pattern, pattern);
        return new SearchResponse(poets, poems);
    }

    private PoetSummary poet(UUID id) {
        PoetSummary result = jdbc.query("""
            select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), p.bio, count(po.id)
            from poet p left join poem po on po.poet_id = p.id where p.id = ?
            group by p.id, p.pen_name, p.full_name, p.bio
            """, rs -> rs.next() ? new PoetSummary(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getInt(4)) : null, id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poet not found.");
        return result;
    }
    private List<PoemSummary> poemsFor(UUID poetId) { return jdbc.query("""
            select id, title, left(regexp_replace(poem, '\\s+', ' ', 'g'), 160) from poem where poet_id = ? order by updated_at desc
            """, (rs, row) -> new PoemSummary(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)), poetId); }
    private PoemDetail poem(UUID id) { PoemDetail result = jdbc.query("""
            select po.id, po.poet_id, po.title, po.poem, coalesce(nullif(p.pen_name, ''), p.full_name), po.legacy_submitted_on, po.created_at
            from poem po join poet p on p.id = po.poet_id where po.id = ?
            """, rs -> rs.next() ? new PoemDetail(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, java.time.LocalDate.class) == null ? rs.getTimestamp(7).toInstant() : rs.getObject(6, java.time.LocalDate.class).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)) : null, id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return result; }
    private static String clientIp(HttpServletRequest request) { String forwarded = request.getHeader("X-Forwarded-For"); return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim(); }

    public record PoetSummary(UUID poetId, String displayName, String bio, int poemCount) {}
    public record PoemSummary(UUID poemId, String title, String excerpt) {}
    public record PoemDetail(UUID poemId, UUID poetId, String title, String poem, String poetDisplayName, Instant createdAt) {}
    public record HomeResponse(PoetSummary poet, List<PoemSummary> poems, PoemDetail selectedPoem) {}
    public record PoetPoemsResponse(PoetSummary poet, List<PoemSummary> poems) {}
    public record PoemSearchResult(UUID poemId, UUID poetId, String title, String poetDisplayName, String excerpt) {}
    public record SearchResponse(List<PoetSummary> poets, List<PoemSearchResult> poems) {}
}
