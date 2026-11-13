package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.support.RaffleFixtures;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the purchase path is safe when many buyers hit the same raffle at the same instant. */
class TicketConcurrencyIT extends RaffleFixtures {

    @Autowired TicketService ticketService;

    private List<TestUser> users(int count) throws Exception {
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(register("Comprador " + i, "comprador" + i + "@x.com"));
        }
        return users;
    }

    /** Runs one task per user, all released at the same time; returns "ok" or "conflict" per task. */
    private List<String> race(List<TestUser> users, java.util.function.Function<TestUser, Object> purchase)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (TestUser user : users) {
                Callable<String> task = () -> {
                    start.await();
                    try {
                        purchase.apply(user);
                        return "ok";
                    } catch (BusinessException e) {
                        return "conflict";
                    }
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twentyBuyersSameNumber_exactlyOneWins() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        UUID raffle = openRaffle(org);
        List<TestUser> buyers = users(20);

        List<String> results = race(buyers,
                u -> ticketService.purchase(raffle, u.id(), new PurchaseRequest(List.of(7), null)));

        assertThat(results).filteredOn("ok"::equals).hasSize(1);
        assertThat(results).filteredOn("conflict"::equals).hasSize(19);
        assertThat(jdbc.queryForObject("select count(*) from tickets where raffle_id = ?", Integer.class, raffle))
                .isEqualTo(1);
    }

    @Test
    void quantityBuyersNeverCollide() throws Exception {
        TestUser org = register("Olga", "olga@x.com");
        Map<String, Object> small = raffleBody();
        small.put("totalNumbers", 10);
        small.put("maxPerUser", 1);
        UUID raffle = openRaffle(org, small);
        List<TestUser> buyers = users(10);

        List<String> results = race(buyers,
                u -> ticketService.purchase(raffle, u.id(), new PurchaseRequest(null, 1)));

        assertThat(results).containsOnly("ok");
        assertThat(jdbc.queryForList("select number from tickets where raffle_id = ? order by number",
                Integer.class, raffle)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }
}
