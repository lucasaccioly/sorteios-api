package com.lucasaccioly.sorteios.draw;

import com.lucasaccioly.sorteios.draw.DrawAlgorithm.SoldTicket;
import com.lucasaccioly.sorteios.raffle.Raffle;
import com.lucasaccioly.sorteios.raffle.RaffleRepository;
import com.lucasaccioly.sorteios.raffle.RaffleStatus;
import com.lucasaccioly.sorteios.ticket.Ticket;
import com.lucasaccioly.sorteios.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Runs due draws. Each draw is its own transaction holding the raffle row lock, so it never runs twice. */
@Service
public class DrawService {

    private static final Logger log = LoggerFactory.getLogger(DrawService.class);

    private final RaffleRepository raffles;
    private final TicketRepository tickets;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    DrawService(RaffleRepository raffles, TicketRepository tickets, Clock clock, PlatformTransactionManager txManager) {
        this.raffles = raffles;
        this.tickets = tickets;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Draws the raffle if it is OPEN and its drawAt has passed; otherwise does nothing. */
    public void drawIfDue(UUID raffleId) {
        Instant now = clock.instant();
        if (!raffles.isDue(raffleId, now)) {
            return;
        }
        newTransaction.executeWithoutResult(status -> raffles.findByIdForUpdate(raffleId)
                .filter(r -> r.getStatus() == RaffleStatus.OPEN && !r.getDrawAt().isAfter(now))
                .ifPresent(r -> draw(r, now)));
    }

    /** Draws every due raffle; returns how many were processed. Failures are logged and skipped. */
    public int drawAllDue() {
        List<UUID> due = raffles.findIdsDue(clock.instant());
        int processed = 0;
        for (UUID id : due) {
            try {
                drawIfDue(id);
                processed++;
            } catch (RuntimeException e) {
                log.error("Draw failed for raffle {}", id, e);
            }
        }
        return processed;
    }

    private void draw(Raffle raffle, Instant now) {
        List<Ticket> sold = tickets.findByRaffleIdOrderByNumberAsc(raffle.getId());
        if (sold.isEmpty()) {
            raffle.closeWithoutSales(now);
            log.info("Raffle {} closed without sales", raffle.getId());
            return;
        }
        List<SoldTicket> entries = sold.stream().map(t -> new SoldTicket(t.getNumber(), t.getUser().getId())).toList();
        String digest = DrawAlgorithm.digest(DrawAlgorithm.ticketsLine(entries));
        Ticket winner = sold.get(DrawAlgorithm.winnerIndex(raffle.getSeed(), digest, sold.size()));
        raffle.drawn(digest, winner.getNumber(), winner.getUser(), now);
        log.info("Raffle {} drawn: number {}", raffle.getId(), winner.getNumber());
    }
}
