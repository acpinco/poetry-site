package com.thinkordrinkpoetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.6-alpine3.23");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheExpectedSchema() {
        var tableNames = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('poet', 'email_login_token', 'user_session')
                """,
                String.class);

        assertThat(tableNames)
                .containsExactlyInAnyOrder("poet", "email_login_token", "user_session");
    }

    @Test
    void generatesUuidV7AndDerivesFullName() {
        UUID poetId = jdbcTemplate.queryForObject(
                """
                INSERT INTO poet (email, first_name, last_name, pen_name)
                VALUES ('maya@example.test', 'Maya', 'Angelou', 'Maya Angelou')
                RETURNING id
                """,
                UUID.class);

        Integer uuidVersion = jdbcTemplate.queryForObject(
                "SELECT uuid_extract_version(CAST(? AS uuid))",
                Integer.class,
                poetId.toString());

        String fullName = jdbcTemplate.queryForObject(
                "SELECT full_name FROM poet WHERE id = ?",
                String.class,
                poetId);

        assertThat(uuidVersion).isEqualTo(7);
        assertThat(fullName).isEqualTo("Maya Angelou");
    }
}
