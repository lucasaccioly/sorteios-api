package com.lucasaccioly.sorteios.draw;

import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.draw.DrawAlgorithm.SoldTicket;
import com.lucasaccioly.sorteios.raffle.Raffle;
import com.lucasaccioly.sorteios.raffle.RaffleService;
import com.lucasaccioly.sorteios.raffle.RaffleStatus;
import com.lucasaccioly.sorteios.ticket.TicketRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class ProofController {

    record ProofResponse(UUID raffleId, String seedHash, String seed, String ticketsDigest,
                         List<SoldTicket> tickets, int soldCount, int winnerIndex, int winningNumber,
                         String winnerName, String algorithm) {
    }

    private final RaffleService raffleService;
    private final TicketRepository tickets;

    ProofController(RaffleService raffleService, TicketRepository tickets) {
        this.raffleService = raffleService;
        this.tickets = tickets;
    }

    /** Everything needed to recompute the winner independently. Only after the draw: the seed is secret before. */
    @GetMapping("/api/raffles/{id}/proof")
    @Transactional(readOnly = true)
    ProofResponse proof(@PathVariable UUID id) {
        Raffle raffle = raffleService.getVisible(id, null);
        if (raffle.getStatus() != RaffleStatus.DRAWN) {
            throw new BusinessException("Prova disponível após o sorteio");
        }
        List<SoldTicket> sold = tickets.findByRaffleIdOrderByNumberAsc(id).stream()
                .map(t -> new SoldTicket(t.getNumber(), t.getUser().getId()))
                .toList();
        int winnerIndex = DrawAlgorithm.winnerIndex(raffle.getSeed(), raffle.getTicketsDigest(), sold.size());
        return new ProofResponse(raffle.getId(), raffle.getSeedHash(), Seeds.hex(raffle.getSeed()),
                raffle.getTicketsDigest(), sold, sold.size(), winnerIndex, raffle.getWinningNumber(),
                raffle.getWinner().getFirstName(), DrawAlgorithm.DESCRIPTION);
    }
}
