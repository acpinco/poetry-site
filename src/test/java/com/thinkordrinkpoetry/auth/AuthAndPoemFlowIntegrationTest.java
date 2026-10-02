package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thinkordrinkpoetry.PoetrySiteApplication;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        classes = PoetrySiteApplication.class,
        properties = {
                "ADMIN_EMAIL=admin@example.com",
                // Every MockMvc request comes from 127.0.0.1, so the per-IP limit would trip across tests.
                "app.auth.magic-link-ip-limit=1000"})
class AuthAndPoemFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.23");

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private MagicLinkMailer magicLinkMailer;

    @MockitoBean
    private TurnstileVerifier turnstileVerifier;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        when(turnstileVerifier.verify(any(), any())).thenReturn(true);
    }

    @Test
    void signInForAnUnknownEmailRespondsNormallyButSendsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/magic-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"stranger@example.com"}
                                """))
                .andExpect(status().isNoContent());

        verify(magicLinkMailer, after(500).never()).send(any(), any());
    }

    @Test
    void signInForAnExistingPoetSendsALink() throws Exception {
        createPoet(requestSession("returning@example.com"), "Returning", "Poet");

        mockMvc.perform(post("/api/auth/magic-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"returning@example.com"}
                                """))
                .andExpect(status().isNoContent());

        verify(magicLinkMailer, timeout(5000).times(2)).send(eq("returning@example.com"), any());
    }

    @Test
    void signUpIsRejectedWhenTheTurnstileChallengeFails() throws Exception {
        when(turnstileVerifier.verify(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bot@example.com","turnstileToken":"forged"}
                                """))
                .andExpect(status().isBadRequest());

        verify(magicLinkMailer, after(500).never()).send(any(), any());
    }

    @Test
    void repeatedRequestsForOneEmailStopSendingWithoutAnError() throws Exception {
        for (int request = 0; request < 6; request++) {
            mockMvc.perform(post("/api/auth/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"flooded@example.com","turnstileToken":"token"}
                                    """))
                    .andExpect(status().isNoContent());
        }

        verify(magicLinkMailer, timeout(5000).times(5)).send(eq("flooded@example.com"), any());
    }

    @Test
    void authenticatedPoetCanCreateUpdateAndDeleteTheirOwnPoem() throws Exception {
        Cookie session = requestSession("poet@example.com");

        mockMvc.perform(post("/api/poets")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Maya","lastName":"Angelou","penName":"Maya","bio":"Poet"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.penName").value("Maya"));

        MvcResult created = mockMvc.perform(post("/api/poems")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Caged Bird","poem":"A free bird leaps"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Caged Bird"))
                .andReturn();

        String poemId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.poemId");
        mockMvc.perform(put("/api/poems/{poemId}", poemId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Caged Bird Revised","poem":"A free bird dares"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Caged Bird Revised"));

        mockMvc.perform(get("/api/poems/{poemId}", poemId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poem").value("A free bird dares"));

        mockMvc.perform(delete("/api/poems/{poemId}", poemId).cookie(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/poems/{poemId}", poemId).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void poemTimestampsAreReturnedAndOnlyUpdatedAtChangesOnEdit() throws Exception {
        Cookie session = requestSession("timestamps@example.com");
        mockMvc.perform(post("/api/poets")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Emily","lastName":"Dickinson"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Emily Dickinson"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        MvcResult created = mockMvc.perform(post("/api/poems")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hope","poem":"Hope is the thing with feathers"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String createdJson = created.getResponse().getContentAsString();
        String poemId = com.jayway.jsonpath.JsonPath.read(createdJson, "$.poemId");
        Instant createdAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(createdJson, "$.createdAt"));
        Instant firstUpdatedAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(createdJson, "$.updatedAt"));

        MvcResult updated = mockMvc.perform(put("/api/poems/{poemId}", poemId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hope","poem":"Hope is the thing with feathers, revised"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String updatedJson = updated.getResponse().getContentAsString();

        assertThat(Instant.parse(com.jayway.jsonpath.JsonPath.read(updatedJson, "$.createdAt"))).isEqualTo(createdAt);
        assertThat(Instant.parse(com.jayway.jsonpath.JsonPath.read(updatedJson, "$.updatedAt")))
                .isAfter(firstUpdatedAt);
    }

    @Test
    void signInRecordsLastSeenWithoutChangingTheProfileUpdatedAt() throws Exception {
        Cookie firstSession = requestSession("visitor@example.com");
        String createdJson = createPoet(firstSession, "Langston", "Hughes").getResponse().getContentAsString();
        Instant firstSeen = Instant.parse(com.jayway.jsonpath.JsonPath.read(createdJson, "$.lastSeenAt"));

        Cookie secondSession = signIn("visitor@example.com");
        String visitedJson = mockMvc.perform(get("/api/poets/me").cookie(secondSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(com.jayway.jsonpath.JsonPath.read(visitedJson, "$.lastSeenAt"))).isAfter(firstSeen);
        assertThat((String) com.jayway.jsonpath.JsonPath.read(visitedJson, "$.updatedAt"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(createdJson, "$.updatedAt"));
        mockMvc.perform(get("/api/auth/me").cookie(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));
        mockMvc.perform(get("/api/admin/poets").cookie(secondSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPenNameCanOnlyBelongToOnePoetRegardlessOfCase() throws Exception {
        Cookie first = requestSession("first-pen@example.com");
        mockMvc.perform(post("/api/poets")
                        .cookie(first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"One","penName":"Night Owl"}
                                """))
                .andExpect(status().isCreated());

        Cookie second = requestSession("second-pen@example.com");
        mockMvc.perform(post("/api/poets")
                        .cookie(second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Bo","lastName":"Two","penName":"night owl"}
                                """))
                .andExpect(status().isConflict());
        createPoet(second, "Bo", "Two");
        mockMvc.perform(put("/api/poets/me")
                        .cookie(second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Bo","lastName":"Two","penName":"NIGHT OWL"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/poets/me")
                        .cookie(first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ann","lastName":"One","penName":"night owl"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penName").value("night owl"));
    }

    @Test
    void unauthenticatedUsersCannotWritePoems() throws Exception {
        mockMvc.perform(post("/api/poems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No session","poem":"No session"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSwaggerDocumentationIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void robotsFileAllowsSearchCrawlersAndReferencesTheSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("User-agent: OAI-SearchBot")))
                .andExpect(content().string(containsString("User-agent: Google-Extended")))
                .andExpect(content().string(containsString("User-agent: GPTBot\nDisallow: /")))
                .andExpect(content().string(containsString("Sitemap: http://localhost:5173/sitemap.xml")));
    }

    @Test
    void poemOfTheDayHasAPublicPageAndSitemapEntry() throws Exception {
        Cookie session = requestSession("daily-poem@example.com");
        createPoet(session, "Daily", "Poet");
        mockMvc.perform(post("/api/poems")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Featured Poem","poem":"A poem for today"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/poem-of-the-day"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Poem of the Day")));
        String firstPick = mockMvc.perform(get("/api/discovery/poem-of-the-day"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/discovery/poem-of-the-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poemId").value(
                        (String) com.jayway.jsonpath.JsonPath.read(firstPick, "$.poemId")));
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/poem-of-the-day")));
    }

    @Test
    void configuredAdminCanListAndLockAnotherPoet() throws Exception {
        Cookie adminSession = requestSession("admin@example.com");
        createPoet(adminSession, "Admin", "Poet");

        Cookie targetSession = requestSession("target@example.com");
        MvcResult target = createPoet(targetSession, "Target", "Poet");
        String targetPoetId = com.jayway.jsonpath.JsonPath.read(
                target.getResponse().getContentAsString(),
                "$.poetId");

        mockMvc.perform(get("/api/auth/me").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));

        mockMvc.perform(get("/api/admin/poets").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].poetId").value(org.hamcrest.Matchers.hasItem(targetPoetId)))
                .andExpect(jsonPath("$[?(@.poetId == '%s')].lastSeenAt".formatted(targetPoetId))
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));

        mockMvc.perform(post("/api/admin/poets/{poetId}/lock", targetPoetId)
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Requested by the account owner"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/poets").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.poetId == '%s')].accountStatus".formatted(targetPoetId))
                        .value(org.hamcrest.Matchers.hasItem("LOCKED")));
    }

    private MvcResult createPoet(Cookie session, String firstName, String lastName) throws Exception {
        return mockMvc.perform(post("/api/poets")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","penName":"%s %s","bio":"Poet"}
                                """.formatted(firstName, lastName, firstName, lastName)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Signs an existing poet in through the sign-in form, returning the new session cookie. */
    private Cookie signIn(String email) throws Exception {
        ArgumentCaptor<String> magicLink = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/auth/magic-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        verify(magicLinkMailer, timeout(5000).times(2)).send(eq(email), magicLink.capture());

        String token = new URI(magicLink.getValue()).getPath().replaceFirst(".*/", "");
        MvcResult login = mockMvc.perform(get("/api/auth/magic-links/{token}", token))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", containsString("/home")))
                .andReturn();
        String cookieValue = login.getResponse().getHeader("Set-Cookie").replaceFirst("^[^=]+=([^;]+).*$", "$1");
        return new Cookie("poetry_session", cookieValue);
    }

    private Cookie requestSession(String email) throws Exception {
        ArgumentCaptor<String> magicLink = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","turnstileToken":"token"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        verify(magicLinkMailer, timeout(5000)).send(eq(email), magicLink.capture());

        String token = new URI(magicLink.getValue()).getPath().replaceFirst(".*/", "");
        MvcResult login = mockMvc.perform(get("/api/auth/magic-links/{token}", token))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", containsString("/account/setup")))
                .andReturn();
        String cookieValue = login.getResponse().getHeader("Set-Cookie").replaceFirst("^[^=]+=([^;]+).*$", "$1");
        return new Cookie("poetry_session", cookieValue);
    }
}
