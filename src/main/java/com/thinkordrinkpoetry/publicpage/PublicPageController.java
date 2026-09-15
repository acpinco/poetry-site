package com.thinkordrinkpoetry.publicpage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Server-rendered public pages give crawlers and shared links useful HTML without requiring JavaScript. */
@Controller
public class PublicPageController {
    private final JdbcTemplate jdbc;
    private final String siteUrl;

    PublicPageController(JdbcTemplate jdbc, @Value("${app.auth.frontend-base-url}") String siteUrl) {
        this.jdbc = jdbc;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = {"/poems/{poemId}", "/poems/{poemId}/{slug}"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String poem(@PathVariable UUID poemId, @PathVariable(required = false) String slug) {
        Poem poem = poemById(poemId);
        String canonical = siteUrl + "/poems/" + poem.id() + "/" + slugify(poem.title());
        return page(poem.title() + " by " + poem.author(), excerpt(poem.body()), canonical,
                "<article><p class=\"byline\">A poem by " + text(poem.author()) + "</p><h1>" + text(poem.title())
                        + "</h1><p class=\"date\">Published " + poem.publishedOn() + "</p><div class=\"poem\">"
                        + text(poem.body()) + "</div><p><a href=\"" + attribute(siteUrl + "/poets/" + poem.poetId() + "/" + slugify(poem.author()))
                        + "\">More poems by " + text(poem.author()) + "</a></p></article>", poemJsonLd(poem, canonical));
    }

    @GetMapping(value = {"/poets/{poetId}", "/poets/{poetId}/{slug}"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String poet(@PathVariable UUID poetId, @PathVariable(required = false) String slug) {
        Poet poet = jdbc.query("""
                select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), p.bio
                from poet p where p.id = ? and exists (select 1 from poem po where po.poet_id = p.id)
                """, rs -> rs.next() ? new Poet(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)) : null, poetId);
        if (poet == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        List<PoemLink> poems = jdbc.query("""
                select id, title from poem where poet_id = ? order by coalesce(legacy_submitted_on, created_at::date) desc, title
                """, (rs, row) -> new PoemLink(rs.getObject(1, UUID.class), rs.getString(2)), poetId);
        String canonical = siteUrl + "/poets/" + poet.id() + "/" + slugify(poet.name());
        String links = poems.stream().map(poem -> "<li><a href=\"" + attribute(siteUrl + "/poems/" + poem.id() + "/" + slugify(poem.title()))
                + "\">" + text(poem.title()) + "</a></li>").reduce("", String::concat);
        return page(poet.name() + " poems", poet.bio() == null || poet.bio().isBlank() ? "Read poems by " + poet.name() + "." : poet.bio(), canonical,
                "<article><h1>" + text(poet.name()) + "</h1>" + (poet.bio() == null || poet.bio().isBlank() ? "" : "<p>" + text(poet.bio()) + "</p>")
                        + "<h2>Poems</h2><ul>" + links + "</ul></article>", poetJsonLd(poet, canonical));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        List<SitemapEntry> poems = jdbc.query("""
                select po.id, po.title, coalesce(nullif(p.pen_name, ''), p.full_name), coalesce(po.legacy_submitted_on, po.updated_at::date)
                from poem po join poet p on p.id = po.poet_id
                """, (rs, row) -> new SitemapEntry("/poems/" + rs.getObject(1, UUID.class) + "/" + slugify(rs.getString(2)), rs.getObject(4, LocalDate.class)));
        List<SitemapEntry> poets = jdbc.query("""
                select p.id, coalesce(nullif(p.pen_name, ''), p.full_name), max(coalesce(po.legacy_submitted_on, po.updated_at::date))
                from poet p join poem po on po.poet_id = p.id group by p.id, p.pen_name, p.full_name
                """, (rs, row) -> new SitemapEntry("/poets/" + rs.getObject(1, UUID.class) + "/" + slugify(rs.getString(2)), rs.getObject(3, LocalDate.class)));
        String entries = java.util.stream.Stream.concat(poems.stream(), poets.stream()).map(entry -> "<url><loc>" + xml(siteUrl + entry.path())
                + "</loc><lastmod>" + entry.lastModified() + "</lastmod></url>").reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" + entries + "</urlset>";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() { return "User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /swagger-ui\nDisallow: /v3/\nSitemap: " + siteUrl + "/sitemap.xml\n"; }

    private Poem poemById(UUID id) {
        Poem poem = jdbc.query("""
                select po.id, po.poet_id, po.title, po.poem, coalesce(nullif(p.pen_name, ''), p.full_name), coalesce(po.legacy_submitted_on, po.created_at::date)
                from poem po join poet p on p.id = po.poet_id where po.id = ?
                """, rs -> rs.next() ? new Poem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, LocalDate.class)) : null, id);
        if (poem == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return poem;
    }

    private String page(String title, String description, String canonical, String body, String structuredData) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + text(title) + " | Think or Drink Poetry</title><meta name=\"description\" content=\"" + attribute(excerpt(description)) + "\">"
                + "<link rel=\"canonical\" href=\"" + attribute(canonical) + "\"><meta property=\"og:type\" content=\"article\"><meta property=\"og:site_name\" content=\"Think or Drink Poetry\">"
                + "<meta property=\"og:title\" content=\"" + attribute(title) + "\"><meta property=\"og:description\" content=\"" + attribute(excerpt(description)) + "\"><meta property=\"og:url\" content=\"" + attribute(canonical) + "\">"
                + "<meta name=\"twitter:card\" content=\"summary\"><script type=\"application/ld+json\">" + safeJson(structuredData) + "</script>"
                + "<style>body{margin:0;background:#080a0f;color:#e4ddd0;font:18px/1.7 Georgia,serif}main{max-width:760px;margin:auto;padding:48px 24px}a{color:#d2b254}h1,h2{line-height:1.2}.byline,.date{font:12px/1.4 system-ui,sans-serif;letter-spacing:.12em;text-transform:uppercase;color:#c9a84c}.date{color:#9896a1}.poem{white-space:pre-wrap;font-style:italic}li{margin:.5rem 0}</style></head><body><main><p><a href=\"" + attribute(siteUrl + "/home") + "\">Think or Drink Poetry</a></p>" + body + "</main></body></html>";
    }

    private static String slugify(String value) { String slug = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); return slug.isBlank() ? "poem" : slug; }
    private static String excerpt(String value) { String compact = value.replaceAll("\\s+", " ").trim(); return compact.length() <= 160 ? compact : compact.substring(0, 157) + "..."; }
    private static String text(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private static String attribute(String value) { return text(value); }
    private static String xml(String value) { return text(value); }
    private static String safeJson(String value) { return value.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026"); }
    private static String jsonString(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    private static String poemJsonLd(Poem poem, String canonical) { return "{\"@context\":\"https://schema.org\",\"@type\":\"CreativeWork\",\"name\":" + jsonString(poem.title()) + ",\"author\":{\"@type\":\"Person\",\"name\":" + jsonString(poem.author()) + "},\"datePublished\":" + jsonString(poem.publishedOn().toString()) + ",\"url\":" + jsonString(canonical) + "}"; }
    private static String poetJsonLd(Poet poet, String canonical) { return "{\"@context\":\"https://schema.org\",\"@type\":\"Person\",\"name\":" + jsonString(poet.name()) + ",\"url\":" + jsonString(canonical) + "}"; }

    private record Poem(UUID id, UUID poetId, String title, String body, String author, LocalDate publishedOn) {}
    private record Poet(UUID id, String name, String bio) {}
    private record PoemLink(UUID id, String title) {}
    private record SitemapEntry(String path, LocalDate lastModified) {}
}
