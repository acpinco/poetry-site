package com.thinkordrinkpoetry.publicpage;

import com.thinkordrinkpoetry.discovery.PoemCatalog;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoemDetail;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoetSummary;
import com.thinkordrinkpoetry.discovery.PoemCatalog.RecentPoemSummary;
import com.thinkordrinkpoetry.discovery.PoemCatalog.SitemapItem;
import com.thinkordrinkpoetry.discovery.PoemOfTheDayService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

/** Server-rendered public pages give crawlers and shared links useful HTML without requiring JavaScript. */
@Controller
public class PublicPageController {
    private static final int LANDING_POEM_COUNT = 12;
    private static final int DESCRIPTION_LENGTH = 160;

    private final PoemCatalog catalog;
    private final PoemOfTheDayService poemOfTheDay;
    private final PublicLinks links;
    private final JsonMapper json;

    PublicPageController(PoemCatalog catalog, PoemOfTheDayService poemOfTheDay, PublicLinks links, JsonMapper json) {
        this.catalog = catalog;
        this.poemOfTheDay = poemOfTheDay;
        this.links = links;
        this.json = json;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String landing(Model model) {
        Map<String, Object> website = schema("WebSite");
        website.put("name", "Think or Drink Poetry");
        website.put("url", links.site());
        website.put("description", "A collection of original poetry from independent voices.");
        model.addAttribute(
                "meta",
                meta(
                        "Original Poetry from Independent Voices",
                        "Discover original poems and poets at Think or Drink Poetry.",
                        links.site() + "/",
                        "website",
                        links.siteImage(),
                        website));
        List<RecentPoemSummary> newThisWeek = catalog.publishedThisWeek(LANDING_POEM_COUNT);
        Set<UUID> shown = newThisWeek.stream().map(RecentPoemSummary::poemId).collect(Collectors.toSet());
        model.addAttribute("poetCount", catalog.publishedPoetCount());
        model.addAttribute("newThisWeek", newThisWeek);
        model.addAttribute(
                "poems",
                catalog.recentlyPublished(LANDING_POEM_COUNT + newThisWeek.size()).stream()
                        .filter(poem -> !shown.contains(poem.poemId()))
                        .limit(LANDING_POEM_COUNT)
                        .toList());
        return page(model, "landing");
    }

    @GetMapping(
            value = {"/poems/{poemId}", "/poems/{poemId}/{slug}"},
            produces = MediaType.TEXT_HTML_VALUE)
    public String poem(@PathVariable UUID poemId, Model model) {
        PoemDetail poem = poemById(poemId);
        String canonical = links.poem(poem.poemId(), poem.title());
        model.addAttribute(
                "meta",
                meta(
                        poem.title() + " by " + poem.poetDisplayName(),
                        poem.poem(),
                        canonical,
                        "article",
                        links.poemImage(poem.poemId()),
                        poemSchema(poem, canonical)));
        model.addAttribute("poem", poem);
        model.addAttribute("publishedOn", publishedOn(poem));
        return page(model, "poem");
    }

    /** "Read another poem": a redirect, so the link itself stays the same on every page load. */
    @GetMapping("/poems/random")
    public String randomPoem(@RequestParam(name = "from", required = false) UUID currentPoemId) {
        return "redirect:"
                + catalog.randomPoem(currentPoemId)
                        .map(poem -> links.poem(poem.poemId(), poem.title()))
                        .orElse(links.home());
    }

    @GetMapping(value = "/poem-of-the-day", produces = MediaType.TEXT_HTML_VALUE)
    public String poemOfTheDay(Model model) {
        UUID poemId = poemOfTheDay.poemIdForToday();
        if (poemId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No poems are available yet.");
        }
        PoemDetail poem = poemById(poemId);
        String canonical = links.poemOfTheDay();
        model.addAttribute(
                "meta",
                meta(
                        "Poem of the Day: " + poem.title() + " by " + poem.poetDisplayName(),
                        "Today’s featured poem: " + poem.title() + " by " + poem.poetDisplayName() + ".",
                        canonical,
                        "article",
                        links.poemImage(poem.poemId()),
                        poemSchema(poem, canonical)));
        model.addAttribute("poem", poem);
        return page(model, "poem-of-the-day");
    }

    @GetMapping(
            value = {"/poets/{poetId}", "/poets/{poetId}/{slug}"},
            produces = MediaType.TEXT_HTML_VALUE)
    public String poet(@PathVariable UUID poetId, Model model) {
        PoetSummary poet = publishedPoet(poetId);
        String canonical = links.poet(poet.poetId(), poet.displayName());
        model.addAttribute(
                "meta",
                meta(
                        poet.displayName() + " poems",
                        hasBio(poet) ? poet.bio() : "Read poems by " + poet.displayName() + ".",
                        canonical,
                        "profile",
                        links.poetImage(poet.poetId()),
                        personSchema(poet, canonical)));
        model.addAttribute("poet", poet);
        model.addAttribute("hasBio", hasBio(poet));
        model.addAttribute("poems", catalog.poemsByPoetByPublication(poetId));
        return page(model, "poet");
    }

    @GetMapping(
            value = {"/poets/{poetId}/bio", "/poets/{poetId}/{slug}/bio"},
            produces = MediaType.TEXT_HTML_VALUE)
    public String poetBio(
            @PathVariable UUID poetId,
            @RequestParam(name = "poem", required = false) UUID returnToPoemId,
            Model model) {
        PoetSummary poet = publishedPoet(poetId);
        String canonical = links.poetBio(poet.poetId(), poet.displayName());
        String bio = hasBio(poet) ? poet.bio() : poet.displayName() + " has not added a biography yet.";
        model.addAttribute(
                "meta",
                meta(
                        poet.displayName() + " bio",
                        bio,
                        canonical,
                        "profile",
                        links.poetImage(poet.poetId()),
                        personSchema(poet, canonical)));
        model.addAttribute("poet", poet);
        model.addAttribute("bio", bio);
        model.addAttribute("returnToPoemId", returnToPoemId);
        return page(model, "poet-bio");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        Stream<SitemapUrl> fixed =
                Stream.of(new SitemapUrl(links.site() + "/", null), new SitemapUrl(links.poemOfTheDay(), null));
        Stream<SitemapUrl> poems = catalog.poemsForSitemap().stream()
                .map(item -> new SitemapUrl(links.poem(item.id(), item.name()), item.lastModified()));
        Stream<SitemapUrl> poets = catalog.poetsForSitemap().stream()
                .map(item -> new SitemapUrl(links.poet(item.id(), item.name()), item.lastModified()));
        Stream<SitemapUrl> bios = catalog.poetBiosForSitemap().stream()
                .map((SitemapItem item) -> new SitemapUrl(links.poetBio(item.id(), item.name()), item.lastModified()));
        String urls = Stream.of(fixed, poems, poets, bios)
                .flatMap(stream -> stream)
                .map(SitemapUrl::toXml)
                .collect(Collectors.joining());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" + urls + "</urlset>";
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /api/
                Disallow: /swagger-ui
                Disallow: /v3/
                Disallow: /poems/random

                User-agent: OAI-SearchBot
                Allow: /
                Disallow: /api/
                Disallow: /swagger-ui
                Disallow: /v3/
                Disallow: /poems/random

                User-agent: Google-Extended
                Allow: /
                Disallow: /api/
                Disallow: /swagger-ui
                Disallow: /v3/
                Disallow: /poems/random

                User-agent: GPTBot
                Disallow: /

                Sitemap: %s/sitemap.xml
                """.formatted(links.site());
    }

    private String page(Model model, String template) {
        model.addAttribute("links", links);
        return "public/" + template;
    }

    private PoemDetail poemById(UUID poemId) {
        return catalog.poem(poemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PoetSummary publishedPoet(UUID poetId) {
        return catalog.publishedPoet(poetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PageMeta meta(
            String title,
            String description,
            String canonicalUrl,
            String openGraphType,
            String imageUrl,
            Map<String, Object> structuredData) {
        // Embedded in a <script> element, so "<" must never appear literally (it could close the tag).
        String jsonLd = json.writeValueAsString(structuredData)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
        return new PageMeta(title, summary(description), canonicalUrl, openGraphType, imageUrl, jsonLd);
    }

    private Map<String, Object> poemSchema(PoemDetail poem, String canonical) {
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("@type", "Person");
        author.put("name", poem.poetDisplayName());
        Map<String, Object> work = schema("CreativeWork");
        work.put("name", poem.title());
        work.put("author", author);
        work.put("datePublished", publishedOn(poem).toString());
        work.put("url", canonical);
        return work;
    }

    private static Map<String, Object> personSchema(PoetSummary poet, String canonical) {
        Map<String, Object> person = schema("Person");
        person.put("name", poet.displayName());
        person.put("url", canonical);
        return person;
    }

    private static Map<String, Object> schema(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("@context", "https://schema.org");
        schema.put("@type", type);
        return schema;
    }

    private static LocalDate publishedOn(PoemDetail poem) {
        return LocalDate.ofInstant(poem.createdAt(), ZoneOffset.UTC);
    }

    private static boolean hasBio(PoetSummary poet) {
        return poet.bio() != null && !poet.bio().isBlank();
    }

    /** A one-line summary for meta descriptions, which search engines truncate around this length. */
    private static String summary(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= DESCRIPTION_LENGTH ? compact : compact.substring(0, DESCRIPTION_LENGTH - 3) + "...";
    }

    /** Page-level metadata rendered into the shared layout's head. */
    public record PageMeta(
            String title,
            String description,
            String canonicalUrl,
            String openGraphType,
            String imageUrl,
            String structuredData) {}

    private record SitemapUrl(String location, LocalDate lastModified) {
        String toXml() {
            return "<url><loc>" + HtmlUtils.htmlEscape(location) + "</loc>"
                    + (lastModified == null ? "" : "<lastmod>" + lastModified + "</lastmod>")
                    + "</url>";
        }
    }
}
