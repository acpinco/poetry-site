package com.thinkordrinkpoetry.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thinkordrinkpoetry.PoetrySiteApplication;
import jakarta.servlet.http.Cookie;
import java.net.URI;
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
        properties = "ADMIN_EMAIL=admin@example.com")
class AuthAndPoemFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.23");

    @Autowired
    private WebApplicationContext applicationContext;

    @MockitoBean
    private MagicLinkMailer magicLinkMailer;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
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
    void configuredAdminCanListAndLockAnotherPoet() throws Exception {
        Cookie adminSession = requestSession("admin@example.com");
        createPoet(adminSession, "Admin", "Poet");

        Cookie targetSession = requestSession("target@example.com");
        MvcResult target = createPoet(targetSession, "Target", "Poet");
        String targetPoetId = com.jayway.jsonpath.JsonPath.read(
                target.getResponse().getContentAsString(),
                "$.poetId");

        mockMvc.perform(get("/api/admin/poets").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].poetId").value(org.hamcrest.Matchers.hasItem(targetPoetId)));

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

    private Cookie requestSession(String email) throws Exception {
        ArgumentCaptor<String> magicLink = ArgumentCaptor.forClass(String.class);
        mockMvc.perform(post("/api/auth/magic-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        verify(magicLinkMailer).send(eq(email), magicLink.capture());

        String token = new URI(magicLink.getValue()).getPath().replaceFirst(".*/", "");
        MvcResult login = mockMvc.perform(get("/api/auth/magic-links/{token}", token))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", containsString("/account/setup")))
                .andReturn();
        String cookieValue = login.getResponse().getHeader("Set-Cookie").replaceFirst("^[^=]+=([^;]+).*$", "$1");
        return new Cookie("poetry_session", cookieValue);
    }
}
