package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.auth.UserRepository;
import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.common.InvalidRequestException;
import com.lucasaccioly.sorteios.common.NotFoundException;
import com.lucasaccioly.sorteios.raffle.Raffle;
import com.lucasaccioly.sorteios.raffle.RaffleRepository;
import com.lucasaccioly.sorteios.raffle.RaffleService;
import com.lucasaccioly.sorteios.raffle.RaffleStatus;
import com.lucasaccioly.sorteios.ticket.TicketDtos.MyTicket;
import com.lucasaccioly.sorteios.ticket.TicketDtos.NumbersResponse;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseRequest;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketService {

    static final int MAX_PER_REQUEST = 50;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RaffleRepository raffles;
    private final RaffleService raffleService;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final Clock clock;

    TicketService(RaffleRepository raffles, RaffleService raffleService, TicketRepository tickets,
                  UserRepository users, Clock clock) {
        this.raffles = raffles;
        this.raffleService = raffleService;
        this.tickets = tickets;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Buys numbers atomically. The raffle row is locked (SELECT ... FOR UPDATE) for the whole transaction, so
     * concurrent purchases and the draw of the same raffle run one at a time; the unique (raffle_id, number)
     * constraint is the last line of defence.
     */
    @Transactional
    public PurchaseResponse purchase(UUID raffleId, UUID userId, PurchaseRequest request) {
        Raffle raffle = raffles.findByIdForUpdate(raffleId)
                .filter(r -> r.getStatus() != RaffleStatus.DRAFT)
                .orElseThrow(NotFoundException::new);
        validateShape(request, raffle.getTotalNumbers());

        Instant now = clock.instant();
        if (raffle.getStatus() != RaffleStatus.OPEN || !now.isBefore(raffle.getDrawAt())) {
            throw new BusinessException("Vendas encerradas");
        }
        if (raffle.isOrganizer(userId)) {
            throw new BusinessException("O organizador não pode comprar números da própria rifa");
        }

        int requested = request.numbers() != null ? request.numbers().size() : request.quantity();
        long alreadyOwned = tickets.countByRaffleIdAndUserId(raffleId, userId);
        if (alreadyOwned + requested > raffle.getMaxPerUser()) {
            throw new BusinessException("Limite de " + raffle.getMaxPerUser() + " números por participante",
                    Map.of("remaining", Math.max(0, raffle.getMaxPerUser() - alreadyOwned)));
        }

        Set<Integer> sold = tickets.findNumbersByRaffleId(raffleId);
        List<Integer> chosen = request.numbers() != null
                ? checkAvailable(request.numbers(), sold)
                : pickRandom(request.quantity(), raffle.getTotalNumbers(), sold);

        var user = users.getReferenceById(userId);
        tickets.saveAllAndFlush(chosen.stream().map(n -> new Ticket(raffle, user, n, now)).toList());
        return new PurchaseResponse(raffleId, chosen);
    }

    @Transactional(readOnly = true)
    public NumbersResponse numbers(UUID raffleId, UUID callerOrNull) {
        Raffle raffle = raffleService.getVisible(raffleId, callerOrNull);
        List<Integer> sold = new ArrayList<>(tickets.findNumbersByRaffleId(raffleId));
        Collections.sort(sold);
        return new NumbersResponse(raffle.getTotalNumbers(), sold);
    }

    @Transactional(readOnly = true)
    public List<MyTicket> mine(UUID userId) {
        return tickets.findMine(userId).stream().map(t -> {
            Raffle r = t.getRaffle();
            boolean won = r.getStatus() == RaffleStatus.DRAWN && Integer.valueOf(t.getNumber()).equals(r.getWinningNumber());
            return new MyTicket(r.getId(), r.getTitle(), t.getNumber(), t.getPurchasedAt(), r.getStatus(), won);
        }).toList();
    }

    private static void validateShape(PurchaseRequest request, int totalNumbers) {
        boolean hasNumbers = request.numbers() != null;
        boolean hasQuantity = request.quantity() != null;
        if (hasNumbers == hasQuantity) {
            throw new InvalidRequestException("numbers", "Informe 'numbers' ou 'quantity' (apenas um dos dois)");
        }
        if (hasQuantity) {
            if (request.quantity() < 1 || request.quantity() > MAX_PER_REQUEST) {
                throw new InvalidRequestException("quantity", "Quantidade entre 1 e " + MAX_PER_REQUEST);
            }
            return;
        }
        List<Integer> numbers = request.numbers();
        if (numbers.isEmpty() || numbers.size() > MAX_PER_REQUEST) {
            throw new InvalidRequestException("numbers", "Escolha entre 1 e " + MAX_PER_REQUEST + " números");
        }
        if (new HashSet<>(numbers).size() != numbers.size()) {
            throw new InvalidRequestException("numbers", "Números repetidos na requisição");
        }
        for (Integer n : numbers) {
            if (n == null || n < 1 || n > totalNumbers) {
                throw new InvalidRequestException("numbers", "Os números vão de 1 a " + totalNumbers);
            }
        }
    }

    private static List<Integer> checkAvailable(List<Integer> requested, Set<Integer> sold) {
        List<Integer> unavailable = requested.stream().filter(sold::contains).sorted().toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException("Alguns números já foram vendidos", Map.of("unavailableNumbers", unavailable));
        }
        return requested.stream().sorted().toList();
    }

    private static List<Integer> pickRandom(int quantity, int totalNumbers, Set<Integer> sold) {
        int free = totalNumbers - sold.size();
        if (quantity > free) {
            throw new BusinessException("Restam apenas " + free + " números");
        }
        List<Integer> candidates = new ArrayList<>(free);
        for (int n = 1; n <= totalNumbers; n++) {
            if (!sold.contains(n)) {
                candidates.add(n);
            }
        }
        Collections.shuffle(candidates, RANDOM);
        return candidates.subList(0, quantity).stream().sorted().toList();
    }
}
