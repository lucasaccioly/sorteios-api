package com.lucasaccioly.sorteios.auth;

import com.lucasaccioly.sorteios.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.admin.email=Admin@x.com", "app.admin.password=admin12345"})
class AdminBootstrapIT extends IntegrationTest {

    @Autowired AdminBootstrap bootstrap;

    @Test
    void createsAdminIdempotently() throws Exception {
        bootstrap.run(null);
        bootstrap.run(null);
        assertThat(jdbc.queryForObject("select count(*) from users where email = 'admin@x.com'", Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@x.com\",\"password\":\"admin12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void promotesExistingUser() throws Exception {
        register("Já Existe", "admin@x.com");
        bootstrap.run(null);
        assertThat(jdbc.queryForObject("select role from users where email = 'admin@x.com'", String.class)).isEqualTo("ADMIN");
    }
}
