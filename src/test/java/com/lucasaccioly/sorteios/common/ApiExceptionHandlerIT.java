package com.lucasaccioly.sorteios.common;

import com.lucasaccioly.sorteios.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerIT extends IntegrationTest {

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.with(SecurityMockMvcRequestPostProcessors.jwt());
    }

    @Test
    void businessException_returns409ProblemDetail() throws Exception {
        mvc.perform(authed(get("/test/errors/business")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Regra violada"))
                .andExpect(jsonPath("$.unavailableNumbers[1]").value(9));
    }

    @Test
    void notFound_returns404() throws Exception {
        mvc.perform(authed(get("/test/errors/not-found")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Recurso não encontrado"));
    }

    @Test
    void invalidBody_returns400WithFieldErrors() throws Exception {
        mvc.perform(authed(post("/test/errors/body")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("Informe o nome"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(authed(post("/test/errors/body")).contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corpo da requisição inválido"));
    }

    @Test
    void health_isUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
