package com.lucasaccioly.sorteios.auth;

import com.lucasaccioly.sorteios.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTest {

    private String body(Object... keyValues) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return json.writeValueAsString(map);
    }

    @Test
    void register_returnsTokenAndUser() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Ana Souza", "email", " Ana@Exemplo.com ", "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void register_duplicateEmailIgnoringCase_returns409() throws Exception {
        register("Ana", "Ana@x.com");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Outra Ana", "email", "ana@x.com", "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void register_invalidInput_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", " ", "email", "nao-e-email", "password", "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_ok_and_wrongPassword_returns401() throws Exception {
        register("Bruno", "bruno@x.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "BRUNO@x.com", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Bruno"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "bruno@x.com", "password", "errada123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "ninguem@x.com", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withToken_returnsUser() throws Exception {
        TestUser carla = register("Carla", "carla@x.com");
        mvc.perform(get("/api/me").header("Authorization", bearer(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(carla.id().toString()))
                .andExpect(jsonPath("$.email").value("carla@x.com"));
    }

    @Test
    void me_withoutToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void me_withMalformedToken_returns401() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void me_withExpiredToken_returns401() throws Exception {
        TestUser dani = register("Dani", "dani@x.com");
        clock.advance(Duration.ofHours(25));
        mvc.perform(get("/api/me").header("Authorization", bearer(dani)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_passwordOver72Bytes_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Ana", "email", "ana@x.com", "password", "é".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
