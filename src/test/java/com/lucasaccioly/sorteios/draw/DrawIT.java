package com.lucasaccioly.sorteios.draw;

import com.lucasaccioly.sorteios.draw.DrawAlgorithm.SoldTicket;
import com.lucasaccioly.sorteios.support.RaffleFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DrawIT extends RaffleFixtures {

    @Autowired DrawService drawService;

    private void buy(TestUser user, UUID raffle, List<Integer> numbers) throws Exception {
        mvc.perform(post("/api/raffles/{id}/tickets", raffle).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("numbers", numbers))))
                .andExpect(status().isCreated());
    }

    /** A raffle with 5 tickets from 3 buyers whose drawAt has just passed. */
    private UUID dueRaffleWithSales() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        buy(register("Ana Lima", "ana@x.com"), raffle, List.of(5, 12));
        buy(register("Bia Reis", "bia@x.com"), raffle, List.of(33));
        buy(register("Caio Dias", "caio@x.com"), raffle, List.of(2, 90));
        clock.advance(Duration.ofDays(7));
        return raffle;
    }

    private JsonNode proof(UUID raffle) throws Exception {
        String body = mvc.perform(get("/api/raffles/{id}/proof", raffle))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static List<SoldTicket> tickets(JsonNode proof) {
        List<SoldTicket> list = new ArrayList<>();
        proof.get("tickets").forEach(t ->
                list.add(new SoldTicket(t.get("number").asInt(), UUID.fromString(t.get("userId").asString()))));
        return list;
    }

    @Test
    void dueRaffle_isDrawnByDrawAllDue_andProofVerifies() throws Exception {
        UUID raffle = dueRaffleWithSales();
        assertThat(drawService.drawAllDue()).isEqualTo(1);

        JsonNode proof = proof(raffle);
        byte[] seed = HexFormat.of().parseHex(proof.get("seed").asString());
        assertThat(Seeds.sha256Hex(seed)).isEqualTo(proof.get("seedHash").asString());
        List<SoldTicket> sold = tickets(proof);
        assertThat(sold).extracting(SoldTicket::number).containsExactly(2, 5, 12, 33, 90);
        String digest = DrawAlgorithm.digest(DrawAlgorithm.ticketsLine(sold));
        assertThat(digest).isEqualTo(proof.get("ticketsDigest").asString());
        int index = DrawAlgorithm.winnerIndex(seed, digest, sold.size());
        assertThat(proof.get("winnerIndex").asInt()).isEqualTo(index);
        assertThat(proof.get("winningNumber").asInt()).isEqualTo(sold.get(index).number());
        assertThat(proof.get("winnerName").asString()).isIn("Ana", "Bia", "Caio");
        assertThat(proof.get("algorithm").asString()).contains("SHA-256");

        mvc.perform(get("/api/raffles/{id}", raffle))
                .andExpect(jsonPath("$.status").value("DRAWN"))
                .andExpect(jsonPath("$.winningNumber").value(sold.get(index).number()))
                .andExpect(jsonPath("$.seed").doesNotExist());

        // Sample consumed by scripts/verify_draw.py during verification.
        Path sample = Path.of("target", "proof-sample.json");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, proof.toString());
    }

    @Test
    void noSales_becomesClosedNoSales() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        clock.advance(Duration.ofDays(7));
        drawService.drawAllDue();
        mvc.perform(get("/api/raffles/{id}", raffle))
                .andExpect(jsonPath("$.status").value("CLOSED_NO_SALES"))
                .andExpect(jsonPath("$.winningNumber").doesNotExist());
    }

    @Test
    void notYetDue_isNotDrawn() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        clock.advance(Duration.ofDays(7).minusSeconds(1));
        assertThat(drawService.drawAllDue()).isZero();
        mvc.perform(get("/api/raffles/{id}", raffle)).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void drawIfDue_isIdempotent() throws Exception {
        UUID raffle = dueRaffleWithSales();
        drawService.drawIfDue(raffle);
        Integer first = jdbc.queryForObject("select winning_number from raffles where id = ?", Integer.class, raffle);
        drawService.drawIfDue(raffle);
        drawService.drawAllDue();
        assertThat(jdbc.queryForObject("select winning_number from raffles where id = ?", Integer.class, raffle))
                .isEqualTo(first);
    }

    @Test
    void getRaffleAfterDrawAt_triggersLazyDraw() throws Exception {
        UUID raffle = dueRaffleWithSales();
        mvc.perform(get("/api/raffles/{id}", raffle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAWN"))
                .andExpect(jsonPath("$.winnerName").exists());
    }

    @Test
    void proof_beforeDraw_returns409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        mvc.perform(get("/api/raffles/{id}/proof", raffle))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Prova disponível após o sorteio"));
        UUID draft = createRaffle(org);
        mvc.perform(get("/api/raffles/{id}/proof", draft)).andExpect(status().isNotFound());
    }

    @Test
    void ticketOwnerChangedAfterDraw_proofNoLongerVerifies() throws Exception {
        UUID raffle = dueRaffleWithSales();
        drawService.drawAllDue();
        UUID intruder = register("Intrusa", "intrusa@x.com").id();
        Integer winning = jdbc.queryForObject("select winning_number from raffles where id = ?", Integer.class, raffle);
        jdbc.update("update tickets set user_id = ? where raffle_id = ? and number = ?", intruder, raffle, winning);

        JsonNode proof = proof(raffle);
        String recomputed = DrawAlgorithm.digest(DrawAlgorithm.ticketsLine(tickets(proof)));
        assertThat(recomputed).isNotEqualTo(proof.get("ticketsDigest").asString());
    }
}
