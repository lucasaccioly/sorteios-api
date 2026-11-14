package com.lucasaccioly.sorteios.demo;

import com.lucasaccioly.sorteios.draw.DrawAlgorithm;
import com.lucasaccioly.sorteios.draw.DrawAlgorithm.SoldTicket;
import com.lucasaccioly.sorteios.draw.Seeds;
import com.lucasaccioly.sorteios.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("demo")
class DemoIT extends IntegrationTest {

    @Autowired DemoSeeder seeder;
    @Autowired DemoMaintenance maintenance;

    @BeforeEach
    void seed() {
        seeder.run(null); // the base class truncates tables after startup seeding
    }

    private List<String> statuses() {
        return jdbc.queryForList("select status from raffles order by status", String.class);
    }

    private int openWithFreeNumbers() {
        return jdbc.queryForObject("""
                select count(*) from raffles r
                where r.status = 'OPEN'
                  and r.total_numbers - (select count(*) from tickets t where t.raffle_id = r.id) >= 10""", Integer.class);
    }

    @Test
    void demoUsersCanLogIn() throws Exception {
        for (String email : List.of("organizador@demo.dev", "participante@demo.dev")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"demo12345\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void seedsEveryStateAndAnOpenRaffle() {
        assertThat(statuses()).contains("DRAWN", "CLOSED_NO_SALES", "CANCELLED", "DRAFT", "OPEN");
        assertThat(openWithFreeNumbers()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void drawnDemoRaffleProofVerifies() throws Exception {
        UUID drawn = jdbc.queryForObject("select id from raffles where status = 'DRAWN'", UUID.class);
        String body = mvc.perform(get("/api/raffles/{id}/proof", drawn))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode proof = json.readTree(body);
        byte[] seed = HexFormat.of().parseHex(proof.get("seed").asString());
        assertThat(Seeds.sha256Hex(seed)).isEqualTo(proof.get("seedHash").asString());
        List<SoldTicket> sold = new ArrayList<>();
        proof.get("tickets").forEach(t ->
                sold.add(new SoldTicket(t.get("number").asInt(), UUID.fromString(t.get("userId").asString()))));
        assertThat(sold.size()).isGreaterThanOrEqualTo(10);
        String digest = DrawAlgorithm.digest(DrawAlgorithm.ticketsLine(sold));
        assertThat(digest).isEqualTo(proof.get("ticketsDigest").asString());
        assertThat(proof.get("winningNumber").asInt())
                .isEqualTo(sold.get(DrawAlgorithm.winnerIndex(seed, digest, sold.size())).number());
    }

    @Test
    void seedingIsIdempotent() {
        int raffles = jdbc.queryForObject("select count(*) from raffles", Integer.class);
        int users = jdbc.queryForObject("select count(*) from users", Integer.class);
        seeder.run(null);
        assertThat(jdbc.queryForObject("select count(*) from raffles", Integer.class)).isEqualTo(raffles);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(users);
    }

    @Test
    void maintenanceReplacesOpenRaffleCloseToItsDraw() {
        assertThat(maintenance.ensureOpenRaffle()).isFalse();
        clock.advance(Duration.ofHours(23).plusMinutes(1));
        assertThat(maintenance.ensureOpenRaffle()).isTrue();
        assertThat(maintenance.ensureOpenRaffle()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from raffles where status = 'OPEN'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void maintenanceReplacesSoldOutRaffle() {
        UUID open = jdbc.queryForObject("select id from raffles where status = 'OPEN'", UUID.class);
        jdbc.update("""
                insert into tickets (id, raffle_id, user_id, number, purchased_at)
                select gen_random_uuid(), ?, (select id from users where email = 'participante@demo.dev'), n, now()
                from generate_series(1, 95) n""", open);
        assertThat(maintenance.ensureOpenRaffle()).isTrue();
        assertThat(openWithFreeNumbers()).isEqualTo(1);
    }

    @Test
    void maintenanceReplacesRaffleWhereDemoParticipantIsNearItsLimit() {
        UUID open = jdbc.queryForObject("select id from raffles where status = 'OPEN'", UUID.class);
        jdbc.update("""
                insert into tickets (id, raffle_id, user_id, number, purchased_at)
                select gen_random_uuid(), ?, (select id from users where email = 'participante@demo.dev'), n, now()
                from generate_series(1, 9) n""", open);
        assertThat(maintenance.ensureOpenRaffle()).isTrue();
        assertThat(maintenance.ensureOpenRaffle()).isFalse();
    }
}
