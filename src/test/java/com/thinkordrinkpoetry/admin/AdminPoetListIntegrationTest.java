package com.thinkordrinkpoetry.admin;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thinkordrinkpoetry.PoetrySiteApplication;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The admin poet list sorts and pages on the server. */
@Testcontainers
@SpringBootTest(classes = PoetrySiteApplication.class, properties = "spring.application.name=admin-list-test")
class AdminPoetListIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.23");

    private static final String SESSION_TOKEN = "admin-list-test-session";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void seed() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
        jdbc.update("delete from poet");
        Instant now = Instant.now();
        UUID admin = poet("admin@example.test", "Admin", "Poet", null, "ADMIN", null, null);
        poet("zed@example.test", "Zed", "Zulu", null, "USER", now.minus(1, ChronoUnit.DAYS), null);
        poet("amy@example.test", "Amelia", "Ames", "amy", "USER", null, null);
        poet("bob@example.test", "Bob", "Brown", null, "USER", now.minus(10, ChronoUnit.DAYS), LocalDate.of(1999, 4, 1));
        // Sign the admin in directly: the session table stores the SHA-256 of the cookie value.
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(SESSION_TOKEN.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                insert into user_session (session_token_hash, authenticated_email, poet_id, expires_at)
                values (?, 'admin@example.test', ?, ?)
                """, hash, admin, Timestamp.from(now.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    void sortsByNameAndPages() throws Exception {
        list("name", "asc", 0, 2)
                .andExpect(jsonPath("$.poets[*].penName", contains("Admin Poet", "amy")))
                .andExpect(jsonPath("$.totalPoets").value(4));
        list("name", "asc", 1, 2)
                .andExpect(jsonPath("$.poets[*].penName", contains("Bob Brown", "Zed Zulu")));
        list("name", "desc", 0, 50)
                .andExpect(jsonPath("$.poets[*].penName", contains("Zed Zulu", "Bob Brown", "amy", "Admin Poet")));
    }

    @Test
    void keepsNeverSeenPoetsLastInBothDirections() throws Exception {
        // The admin's own request marks them seen just now.
        list("lastSeen", "desc", 0, 50)
                .andExpect(jsonPath("$.poets[*].penName", contains("Admin Poet", "Zed Zulu", "Bob Brown", "amy")))
                .andExpect(jsonPath("$.seenLastWeek").value(2));
        list("lastSeen", "asc", 0, 50)
                .andExpect(jsonPath("$.poets[*].penName", contains("Bob Brown", "Zed Zulu", "Admin Poet", "amy")));
    }

    @Test
    void sortsByJoinedUsingTheOriginal1999Date() throws Exception {
        list("joined", "asc", 0, 1).andExpect(jsonPath("$.poets[*].penName", contains("Bob Brown")));
    }

    @Test
    void rejectsAnUnknownSort() throws Exception {
        list("password", "asc", 0, 50)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sort by name, email, status, joined, or lastSeen."));
    }

    private org.springframework.test.web.servlet.ResultActions list(String sort, String direction, int page, int size)
            throws Exception {
        return mockMvc.perform(get("/api/admin/poets")
                        .cookie(new Cookie("poetry_session", SESSION_TOKEN))
                        .param("sort", sort)
                        .param("direction", direction)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)));
    }

    private UUID poet(String email, String firstName, String lastName, String penName, String role, Instant lastSeenAt,
            LocalDate legacySubmittedOn) {
        return jdbc.queryForObject("""
                insert into poet (email, first_name, last_name, pen_name, role, last_seen_at, legacy_submitted_on)
                values (?, ?, ?, ?, ?, ?, ?) returning id
                """, UUID.class, email, firstName, lastName, penName, role,
                lastSeenAt == null ? null : Timestamp.from(lastSeenAt), legacySubmittedOn);
    }
}
