package com.lucasaccioly.sorteios.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base class for integration tests: real PostgreSQL (embedded), MockMvc and a controllable clock. */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    public static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected ObjectMapper json;

    @BeforeEach
    void resetState() {
        clock.set(START);
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
        }
    }

    public record TestUser(UUID id, String token) {}

    /** Registers a user through the real endpoint (password "senha12345"). */
    protected TestUser register(String name, String email) throws Exception {
        String body = json.writeValueAsString(Map.of("name", name, "email", email, "password", PASSWORD));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return new TestUser(UUID.fromString(node.get("user").get("id").asString()), node.get("token").asString());
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    public static final String PASSWORD = "senha12345";

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }
}
