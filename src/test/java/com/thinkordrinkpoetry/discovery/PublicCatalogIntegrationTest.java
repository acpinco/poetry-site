package com.thinkordrinkpoetry.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thinkordrinkpoetry.PoetrySiteApplication;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Pins down what the public discovery API and server-rendered pages return, so refactoring their
 * queries and rendering cannot silently change what visitors and search engines see.
 */
@Testcontainers
@SpringBootTest(classes = PoetrySiteApplication.class)
class PublicCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.23");

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private UUID maya;
    private UUID robert;
    private UUID empty;
    private UUID stillIRise;
    private UUID legacyPoem;
    private UUID road;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        jdbc.update("delete from poet");
        maya = poet("maya@example.test", "Maya", "Angelou", "Caged Bird", "Poet & teacher <b>bold</b>");
        robert = poet("robert@example.test", "Robert", "Frost", null, null);
        empty = poet("empty@example.test", "Empty", "Poet", null, "Has no poems");
        // Inserted one at a time, so created_at increases in this order.
        stillIRise = poem(maya, "Still I Rise", "You may write me down\nin history", null);
        legacyPoem = poem(maya, "Old <Verse>", "Line one   two", LocalDate.of(1999, 3, 12));
        road = poem(robert, "Road", "Two roads diverged", null);
    }

    @Test
    void poetDirectoryListsOnlyPoetsWithPoemsByDisplayName() throws Exception {
        mockMvc.perform(get("/api/discovery/poets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].displayName", contains("Caged Bird", "Robert Frost")))
                .andExpect(jsonPath("$[*].poemCount", contains(2, 1)))
                .andExpect(jsonPath("$[0].bio").value("Poet & teacher <b>bold</b>"));
    }

    @Test
    void poetPoemsUseLegacyDatesAndCompactExcerpts() throws Exception {
        mockMvc.perform(get("/api/discovery/poets/{id}/poems", maya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poet.displayName").value("Caged Bird"))
                .andExpect(jsonPath("$.poet.poemCount").value(2))
                .andExpect(jsonPath("$.poems", hasSize(2)))
                .andExpect(jsonPath("$.poems[?(@.title == 'Old <Verse>')].createdAt")
                        .value(org.hamcrest.Matchers.contains("1999-03-12T00:00:00Z")))
                .andExpect(jsonPath("$.poems[?(@.title == 'Old <Verse>')].excerpt")
                        .value(org.hamcrest.Matchers.contains("Line one two")));
    }

    @Test
    void aPoetWithoutPoemsHasAnEmptyListButStillExists() throws Exception {
        mockMvc.perform(get("/api/discovery/poets/{id}/poems", empty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poet.displayName").value("Empty Poet"))
                .andExpect(jsonPath("$.poet.poemCount").value(0))
                .andExpect(jsonPath("$.poems", hasSize(0)));
        mockMvc.perform(get("/api/discovery/poets/{id}/poems", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void poemDetailIncludesThePoetAndPublishedDate() throws Exception {
        mockMvc.perform(get("/api/discovery/poems/{id}", legacyPoem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poemId").value(legacyPoem.toString()))
                .andExpect(jsonPath("$.poetId").value(maya.toString()))
                .andExpect(jsonPath("$.title").value("Old <Verse>"))
                .andExpect(jsonPath("$.poem").value("Line one   two"))
                .andExpect(jsonPath("$.poetDisplayName").value("Caged Bird"))
                .andExpect(jsonPath("$.poetBio").value("Poet & teacher <b>bold</b>"))
                .andExpect(jsonPath("$.createdAt").value("1999-03-12T00:00:00Z"));
        mockMvc.perform(get("/api/discovery/poems/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void recentPoemsAreNewestFirstAndPaged() throws Exception {
        mockMvc.perform(get("/api/discovery/poems/recent").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.poems[*].title", contains("Road", "Old <Verse>")))
                .andExpect(jsonPath("$.poems[0].poetDisplayName").value("Robert Frost"))
                .andExpect(jsonPath("$.poems[1].createdAt").value("1999-03-12T00:00:00Z"));
        mockMvc.perform(get("/api/discovery/poems/recent").param("limit", "2").param("offset", "2"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.poems[*].title", contains("Still I Rise")));
    }

    @Test
    void searchMatchesPoetNamesAndPoemText() throws Exception {
        mockMvc.perform(get("/api/discovery/search").param("q", "frost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poets[*].displayName", contains("Robert Frost")))
                .andExpect(jsonPath("$.poems", hasSize(0)));
        mockMvc.perform(get("/api/discovery/search").param("q", "HISTORY"))
                .andExpect(jsonPath("$.poets", hasSize(0)))
                .andExpect(jsonPath("$.poems[*].title", contains("Still I Rise")))
                .andExpect(jsonPath("$.poems[0].poetDisplayName").value("Caged Bird"));
    }

    @Test
    void searchTreatsLikeWildcardsAsPlainText() throws Exception {
        mockMvc.perform(get("/api/discovery/search").param("q", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poets", hasSize(0)))
                .andExpect(jsonPath("$.poems", hasSize(0)));
        mockMvc.perform(get("/api/discovery/search").param("q", "__")).andExpect(jsonPath("$.poems", hasSize(0)));
    }

    @Test
    void homePicksAPoetWithPoemsAndOpensOneOfTheirPoems() throws Exception {
        mockMvc.perform(get("/api/discovery/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poet.poemCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.selectedPoem.poetDisplayName").exists())
                .andExpect(jsonPath("$.poems").isNotEmpty());
    }

    @Test
    void poemPageIsEscapedAndCanonical() throws Exception {
        mockMvc.perform(get("/poems/{id}/any-slug", legacyPoem))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("<!doctype html>")))
                .andExpect(content().string(containsString("Old &lt;Verse&gt; by Caged Bird | Think or Drink Poetry")))
                .andExpect(
                        content().string(containsString("\"author\":{\"@type\":\"Person\",\"name\":\"Caged Bird\"}")))
                .andExpect(content()
                        .string(containsString("<link rel=\"canonical\" href=\"http://localhost:5173/poems/"
                                + legacyPoem + "/old-verse\">")))
                .andExpect(content().string(containsString("A poem by Caged Bird")))
                .andExpect(content().string(containsString("Published 1999-03-12")))
                .andExpect(content().string(containsString("\"datePublished\":\"1999-03-12\"")))
                .andExpect(content()
                        .string(containsString("href=\"http://localhost:5173/poets/" + maya + "/caged-bird\"")))
                .andExpect(content().string(not(containsString("<Verse>"))))
                .andExpect(content()
                        .string(containsString("<meta property=\"og:image\" content=\"http://localhost:5173/poems/"
                                + legacyPoem + "/share.png\">")))
                .andExpect(content()
                        .string(containsString("<meta name=\"twitter:card\" content=\"summary_large_image\">")))
                .andExpect(content()
                        .string(containsString("href=\"http://localhost:5173/poems/random?from=" + legacyPoem + "\"")))
                .andExpect(content().string(containsString("href=\"http://localhost:5173/sign-in?join=1\"")));
    }

    @Test
    void shareImagesArePngCardsThatCacheForADay() throws Exception {
        for (String path :
                new String[] {"/share.png", "/poems/" + legacyPoem + "/share.png", "/poets/" + maya + "/share.png"}) {
            byte[] png = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG))
                    .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).as(path).isEqualTo(1200);
            assertThat(image.getHeight()).as(path).isEqualTo(630);
        }
        mockMvc.perform(get("/poems/{id}/share.png", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/poets/{id}/share.png", empty)).andExpect(status().isNotFound());
    }

    @Test
    void readAnotherPoemRedirectsToADifferentPoem() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/poems/random").param("from", road.toString()))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrlPattern("http://localhost:5173/poems/*/*"))
                    .andExpect(header().string("Location", not(containsString(road.toString()))));
        }
        jdbc.update("delete from poem where id <> ?", road);
        mockMvc.perform(get("/poems/random").param("from", road.toString()))
                .andExpect(redirectedUrl("http://localhost:5173/home"));
    }

    @Test
    void poetPageListsPoemsAndEscapesTheBio() throws Exception {
        mockMvc.perform(get("/poets/{id}/whatever", maya))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Caged Bird poems | Think or Drink Poetry")))
                .andExpect(content().string(containsString("Poet &amp; teacher &lt;b&gt;bold&lt;/b&gt;")))
                .andExpect(content().string(containsString("/poems/" + stillIRise + "/still-i-rise")))
                .andExpect(content().string(containsString("/poems/" + legacyPoem + "/old-verse")))
                .andExpect(content().string(not(containsString("<b>bold</b>"))));
        mockMvc.perform(get("/poets/{id}", empty)).andExpect(status().isNotFound());
    }

    @Test
    void bioPageFallsBackWhenThePoetHasNoBio() throws Exception {
        mockMvc.perform(get("/poets/{id}/robert-frost/bio", robert).param("poem", road.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Robert Frost has not added a biography yet.")))
                .andExpect(content().string(containsString("http://localhost:5173/home?poem=" + road)));
    }

    @Test
    void landingPageSummarisesTheLibrary() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Browse poems by 2 poets")))
                .andExpect(content().string(containsString("Still I Rise")))
                .andExpect(content().string(containsString("Old &lt;Verse&gt;")))
                .andExpect(content()
                        .string(containsString(
                                "<meta property=\"og:image\" content=\"http://localhost:5173/share.png\">")))
                .andExpect(content().string(containsString("href=\"http://localhost:5173/sign-in?join=1\"")));
    }

    @Test
    void landingPageListsPoemsPublishedThisWeekSeparately() throws Exception {
        // The 1999 poem was added today too, but it counts by its original date.
        String page = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String thisWeek =
                page.substring(page.indexOf("New this week"), page.indexOf("Recently added to the collection"));
        assertThat(thisWeek).contains("Still I Rise", "Road").doesNotContain("Old &lt;Verse&gt;");
        assertThat(page.substring(page.indexOf("Recently added to the collection")))
                .contains("Old &lt;Verse&gt;")
                .doesNotContain("Still I Rise");

        jdbc.update("update poem set created_at = now() - interval '8 days'");
        mockMvc.perform(get("/")).andExpect(content().string(not(containsString("New this week"))));
    }

    @Test
    void robotsKeepsCrawlersOffTheRandomPoemRedirect() throws Exception {
        mockMvc.perform(get("/robots.txt")).andExpect(content().string(containsString("Disallow: /poems/random")));
    }

    @Test
    void sitemapListsPoemsPoetsAndOnlyBiosThatExist() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/poems/" + road + "/road</loc>")))
                .andExpect(content().string(containsString("/poets/" + robert + "/robert-frost</loc>")))
                .andExpect(content().string(containsString("/poets/" + maya + "/caged-bird/bio</loc>")))
                .andExpect(content().string(not(containsString("/poets/" + robert + "/robert-frost/bio"))))
                .andExpect(content().string(not(containsString(empty.toString()))));
    }

    private UUID poet(String email, String firstName, String lastName, String penName, String bio) {
        return jdbc.queryForObject("""
                insert into poet (email, first_name, last_name, pen_name, bio)
                values (?, ?, ?, ?, ?) returning id
                """, UUID.class, email, firstName, lastName, penName, bio);
    }

    private UUID poem(UUID poetId, String title, String body, LocalDate legacySubmittedOn) {
        return jdbc.queryForObject("""
                insert into poem (poet_id, title, poem, legacy_submitted_on)
                values (?, ?, ?, ?) returning id
                """, UUID.class, poetId, title, body, legacySubmittedOn);
    }
}
