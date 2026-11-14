package com.lucasaccioly.sorteios.raffle;

import com.lucasaccioly.sorteios.support.RaffleFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RaffleIT extends RaffleFixtures {

    @Test
    void create_returnsDraft_hiddenFromOthersAndPublicList() throws Exception {
        TestUser org = register("Olga Organizadora", "olga@x.com");
        TestUser other = register("Paulo", "paulo@x.com");
        UUID id = createRaffle(org);

        mvc.perform(get("/api/raffles/{id}", id).header("Authorization", bearer(org)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.organizer.name").value("Olga Organizadora"))
                .andExpect(jsonPath("$.ticketPriceCents").value(500))
                .andExpect(jsonPath("$.soldCount").value(0))
                .andExpect(jsonPath("$.availableCount").value(100))
                .andExpect(jsonPath("$.seedHash").doesNotExist());
        mvc.perform(get("/api/raffles/{id}", id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/raffles/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/raffles")).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void create_requiresAuth() throws Exception {
        mvc.perform(post("/api/raffles").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(raffleBody())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_invalid_returns400() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        for (Map.Entry<String, Object> bad : Map.<String, Object>of(
                "totalNumbers", 9,
                "maxPerUser", 0,
                "ticketPriceCents", -1,
                "title", " ").entrySet()) {
            Map<String, Object> body = raffleBody();
            body.put(bad.getKey(), bad.getValue());
            mvc.perform(post("/api/raffles").header("Authorization", bearer(org))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + bad.getKey()).exists());
        }
        Map<String, Object> tooMany = raffleBody();
        tooMany.put("totalNumbers", 10001);
        mvc.perform(post("/api/raffles").header("Authorization", bearer(org))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tooMany)))
                .andExpect(status().isBadRequest());

        Map<String, Object> past = raffleBody();
        past.put("drawAt", START.minusSeconds(1).toString());
        mvc.perform(post("/api/raffles").header("Authorization", bearer(org))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(past)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.drawAt").exists());

        Map<String, Object> limitAboveTotal = raffleBody();
        limitAboveTotal.put("maxPerUser", 101);
        mvc.perform(post("/api/raffles").header("Authorization", bearer(org))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(limitAboveTotal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxPerUser").exists());
    }

    @Test
    void update_onlyInDraft_andOnlyByOrganizer() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser other = register("Paulo", "paulo@x.com");
        UUID id = createRaffle(org);
        Map<String, Object> body = raffleBody();
        body.put("title", "Rifa da TV");

        mvc.perform(put("/api/raffles/{id}", id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/raffles/{id}", id).header("Authorization", bearer(org))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rifa da TV"));

        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(org)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/raffles/{id}", id).header("Authorization", bearer(org))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_setsOpenAndSeedHash_neverExposesSeed() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID id = createRaffle(org);
        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(org)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.seedHash").value(matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.seed").doesNotExist());
        mvc.perform(get("/api/raffles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.seed").doesNotExist());
        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(org)))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_byOtherUser_returns404() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser other = register("Paulo", "paulo@x.com");
        UUID id = createRaffle(org);
        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publish_withPastDrawAt_returns409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID id = createRaffle(org);
        clock.advance(Duration.ofDays(8));
        org = login("olga@x.com");
        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(org)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_draftOrOpenWithoutSales_ok() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID draft = createRaffle(org);
        UUID open = openRaffle(org);
        mvc.perform(post("/api/raffles/{id}/cancel", draft).header("Authorization", bearer(org)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/raffles/{id}/cancel", open).header("Authorization", bearer(org)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/raffles/{id}/cancel", open).header("Authorization", bearer(org)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_byOtherUser_returns404() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser other = register("Paulo", "paulo@x.com");
        UUID open = openRaffle(org);
        mvc.perform(post("/api/raffles/{id}/cancel", open).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_byAdmin_ok() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        register("Admin", "admin@x.com");
        jdbc.update("update users set role = 'ADMIN' where email = 'admin@x.com'");
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@x.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String adminToken = json.readTree(login).get("token").asString();
        UUID open = openRaffle(org);
        mvc.perform(post("/api/raffles/{id}/cancel", open).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void list_filtersByStatusAndQuery_paginates_neverDraft() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        createRaffle(org);
        Map<String, Object> tv = raffleBody();
        tv.put("title", "Rifa da TV");
        tv.put("drawAt", START.plus(Duration.ofDays(3)).toString());
        openRaffle(org, tv);
        openRaffle(org);
        UUID cancelled = openRaffle(org);
        mvc.perform(post("/api/raffles/{id}/cancel", cancelled).header("Authorization", bearer(org)));

        mvc.perform(get("/api/raffles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Rifa da TV"));
        mvc.perform(get("/api/raffles").param("status", "OPEN"))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/raffles").param("q", "tv"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Rifa da TV"));
        mvc.perform(get("/api/raffles").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/raffles").param("size", "500"))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/raffles").param("status", "DRAFT"))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/raffles").param("sort", "naoExiste"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mine_listsAllOwnStatuses() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser other = register("Paulo", "paulo@x.com");
        createRaffle(org);
        openRaffle(org);
        openRaffle(other);
        mvc.perform(get("/api/raffles/mine").header("Authorization", bearer(org)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/raffles/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void sort_onlyWhitelistedProperties_noNestedPaths() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        openRaffle(org);
        for (String sort : new String[]{"organizer.email", "winner.email", "organizer.passwordHash", "seed"}) {
            mvc.perform(get("/api/raffles").param("sort", sort)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/raffles/mine").param("sort", sort).header("Authorization", bearer(org)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/raffles").param("sort", "title,desc")).andExpect(status().isOk());
        mvc.perform(get("/api/raffles").param("sort", "ticketPriceCents")).andExpect(status().isOk());
    }
}
