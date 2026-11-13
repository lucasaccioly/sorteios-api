package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.raffle.RaffleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketDtos {

    private TicketDtos() {
    }

    /** Exactly one of {@code numbers} (chosen numbers) or {@code quantity} (random free numbers). */
    public record PurchaseRequest(List<Integer> numbers, Integer quantity) {
    }

    public record PurchaseResponse(UUID raffleId, List<Integer> numbers) {
    }

    public record NumbersResponse(int totalNumbers, List<Integer> sold) {
    }

    public record MyTicket(UUID raffleId, String raffleTitle, int number, Instant purchasedAt,
                           RaffleStatus raffleStatus, boolean won) {
    }
}
