package com.lucasaccioly.sorteios.support;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers to create and publish raffles through the API. */
public abstract class RaffleFixtures extends IntegrationTest {

    protected Map<String, Object> raffleBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Rifa da Bicicleta");
        body.put("description", "Bicicleta aro 29");
        body.put("prize", "Bicicleta");
        body.put("ticketPriceCents", 500);
        body.put("totalNumbers", 100);
        body.put("maxPerUser", 10);
        body.put("drawAt", START.plus(Duration.ofDays(7)).toString());
        return body;
    }

    protected UUID createRaffle(TestUser organizer, Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/raffles").header("Authorization", bearer(organizer))
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asString());
    }

    protected UUID createRaffle(TestUser organizer) throws Exception {
        return createRaffle(organizer, raffleBody());
    }

    protected UUID openRaffle(TestUser organizer, Map<String, Object> body) throws Exception {
        UUID id = createRaffle(organizer, body);
        mvc.perform(post("/api/raffles/{id}/publish", id).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
        return id;
    }

    protected UUID openRaffle(TestUser organizer) throws Exception {
        return openRaffle(organizer, raffleBody());
    }
}
