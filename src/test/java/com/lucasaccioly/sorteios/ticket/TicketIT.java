package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.support.RaffleFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketIT extends RaffleFixtures {

    private ResultActions buy(TestUser user, UUID raffleId, Object body) throws Exception {
        return mvc.perform(post("/api/raffles/{id}/tickets", raffleId).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private int soldCount(UUID raffleId) {
        return jdbc.queryForObject("select count(*) from tickets where raffle_id = ?", Integer.class, raffleId);
    }

    @Test
    void buyChosenNumbers_ok_andNumbersMapShowsThem() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        UUID raffle = openRaffle(org);
        buy(ana, raffle, Map.of("numbers", List.of(42, 7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.raffleId").value(raffle.toString()))
                .andExpect(jsonPath("$.numbers", contains(7, 42)));
        mvc.perform(get("/api/raffles/{id}/numbers", raffle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNumbers").value(100))
                .andExpect(jsonPath("$.sold", contains(7, 42)));
        mvc.perform(get("/api/raffles/{id}", raffle))
                .andExpect(jsonPath("$.soldCount").value(2))
                .andExpect(jsonPath("$.availableCount").value(98));
    }

    @Test
    void buyQuantity_picksFreeDistinctNumbers() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        TestUser bia = register("Bia", "bia@x.com");
        Map<String, Object> small = raffleBody();
        small.put("totalNumbers", 10);
        small.put("maxPerUser", 10);
        UUID raffle = openRaffle(org, small);
        buy(ana, raffle, Map.of("numbers", List.of(1, 2, 3, 4, 5, 6, 7))).andExpect(status().isCreated());
        buy(bia, raffle, Map.of("quantity", 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numbers", contains(8, 9, 10)));
        assertThat(soldCount(raffle)).isEqualTo(10);
    }

    @Test
    void numberAlreadySold_returns409WithUnavailableNumbers_andSavesNothing() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        TestUser bia = register("Bia", "bia@x.com");
        UUID raffle = openRaffle(org);
        buy(ana, raffle, Map.of("numbers", List.of(4, 9))).andExpect(status().isCreated());
        buy(bia, raffle, Map.of("numbers", List.of(9, 3, 4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.unavailableNumbers", contains(4, 9)));
        assertThat(soldCount(raffle)).isEqualTo(2);
    }

    @Test
    void duplicateOrOutOfRange_returns400_savesNothing() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        UUID raffle = openRaffle(org);
        List<Integer> fiftyOne = new ArrayList<>(IntStream.rangeClosed(1, 51).boxed().toList());
        Map<String, Object> both = Map.of("numbers", List.of(1), "quantity", 1);
        for (Object body : List.of(
                Map.of("numbers", List.of(7, 7)),
                Map.of("numbers", List.of(0)),
                Map.of("numbers", List.of(101)),
                Map.of("numbers", List.of()),
                Map.of("numbers", fiftyOne),
                Map.of("quantity", 0),
                Map.of("quantity", 51),
                both,
                Map.of())) {
            buy(ana, raffle, body).andExpect(status().isBadRequest());
        }
        assertThat(soldCount(raffle)).isZero();
    }

    @Test
    void quantityMoreThanFree_returns409_savesNothing() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        TestUser bia = register("Bia", "bia@x.com");
        Map<String, Object> small = raffleBody();
        small.put("totalNumbers", 10);
        small.put("maxPerUser", 10);
        UUID raffle = openRaffle(org, small);
        buy(ana, raffle, Map.of("quantity", 8)).andExpect(status().isCreated());
        buy(bia, raffle, Map.of("quantity", 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Restam apenas 2 números"));
        assertThat(soldCount(raffle)).isEqualTo(8);
    }

    @Test
    void perUserLimit_returns409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        Map<String, Object> body = raffleBody();
        body.put("maxPerUser", 3);
        UUID raffle = openRaffle(org, body);
        buy(ana, raffle, Map.of("numbers", List.of(1, 2))).andExpect(status().isCreated());
        buy(ana, raffle, Map.of("numbers", List.of(3, 4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Limite de 3 números por participante"));
        buy(ana, raffle, Map.of("numbers", List.of(3))).andExpect(status().isCreated());
    }

    @Test
    void organizerCannotBuy_returns409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        buy(org, raffle, Map.of("numbers", List.of(1))).andExpect(status().isConflict());
    }

    @Test
    void draftOrCancelled_returns404or409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        UUID draft = createRaffle(org);
        buy(ana, draft, Map.of("numbers", List.of(1))).andExpect(status().isNotFound());
        mvc.perform(get("/api/raffles/{id}/numbers", draft)).andExpect(status().isNotFound());
        UUID cancelled = openRaffle(org);
        mvc.perform(post("/api/raffles/{id}/cancel", cancelled).header("Authorization", bearer(org)));
        buy(ana, cancelled, Map.of("numbers", List.of(1))).andExpect(status().isConflict());
        buy(ana, UUID.randomUUID(), Map.of("numbers", List.of(1))).andExpect(status().isNotFound());
    }

    @Test
    void afterDrawAt_beforeScheduler_returns409() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        clock.advance(java.time.Duration.ofDays(7));
        TestUser ana = register("Ana", "ana@x.com");
        buy(ana, raffle, Map.of("numbers", List.of(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Vendas encerradas"));
        assertThat(soldCount(raffle)).isZero();
    }

    @Test
    void cancelWithSales_returns409_evenForAdmin() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        register("Admin", "admin@x.com");
        jdbc.update("update users set role = 'ADMIN' where email = 'admin@x.com'");
        TestUser admin = login("admin@x.com");
        UUID raffle = openRaffle(org);
        buy(ana, raffle, Map.of("numbers", List.of(5))).andExpect(status().isCreated());
        mvc.perform(post("/api/raffles/{id}/cancel", raffle).header("Authorization", bearer(org)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Não é possível cancelar uma rifa com números vendidos"));
        mvc.perform(post("/api/raffles/{id}/cancel", raffle).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void mine_listsOwnTickets() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        TestUser ana = register("Ana", "ana@x.com");
        TestUser bia = register("Bia", "bia@x.com");
        UUID raffle = openRaffle(org);
        buy(ana, raffle, Map.of("numbers", List.of(10, 20))).andExpect(status().isCreated());
        buy(bia, raffle, Map.of("numbers", List.of(30))).andExpect(status().isCreated());
        mvc.perform(get("/api/tickets/mine").header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].raffleTitle").value("Rifa da Bicicleta"))
                .andExpect(jsonPath("$[0].raffleStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].won").value(false));
        mvc.perform(get("/api/tickets/mine")).andExpect(status().isUnauthorized());
    }
}
