package com.lucasaccioly.sorteios.raffle;

import com.lucasaccioly.sorteios.auth.UserRepository;
import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.common.InvalidRequestException;
import com.lucasaccioly.sorteios.common.NotFoundException;
import com.lucasaccioly.sorteios.draw.Seeds;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.PageResponse;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.Person;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleRequest;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleResponse;
import com.lucasaccioly.sorteios.ticket.TicketRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class RaffleService {

    private final RaffleRepository raffles;
    private final UserRepository users;
    private final TicketRepository tickets;
    private final Clock clock;

    RaffleService(RaffleRepository raffles, UserRepository users, TicketRepository tickets, Clock clock) {
        this.raffles = raffles;
        this.users = users;
        this.tickets = tickets;
        this.clock = clock;
    }

    @Transactional
    public RaffleResponse create(UUID callerId, RaffleRequest request) {
        validate(request);
        Raffle raffle = new Raffle(users.getReferenceById(callerId), clock.instant());
        apply(raffle, request);
        return toResponse(raffles.save(raffle));
    }

    @Transactional
    public RaffleResponse update(UUID raffleId, UUID callerId, RaffleRequest request) {
        Raffle raffle = raffles.findByIdForUpdate(raffleId).filter(r -> r.isOrganizer(callerId))
                .orElseThrow(NotFoundException::new);
        if (raffle.getStatus() != RaffleStatus.DRAFT) {
            throw new BusinessException("Somente rascunhos podem ser editados");
        }
        validate(request);
        apply(raffle, request);
        return toResponse(raffle);
    }

    @Transactional
    public RaffleResponse publish(UUID raffleId, UUID callerId) {
        Raffle raffle = raffles.findByIdForUpdate(raffleId).filter(r -> r.isOrganizer(callerId))
                .orElseThrow(NotFoundException::new);
        if (raffle.getStatus() != RaffleStatus.DRAFT) {
            throw new BusinessException("Somente rascunhos podem ser publicados");
        }
        if (!raffle.getDrawAt().isAfter(clock.instant())) {
            throw new BusinessException("A data do sorteio já passou; edite a rifa antes de publicar");
        }
        byte[] seed = Seeds.newSeed();
        raffle.open(seed, Seeds.sha256Hex(seed));
        return toResponse(raffle);
    }

    @Transactional
    public RaffleResponse cancel(UUID raffleId, UUID callerId, boolean callerIsAdmin) {
        Raffle raffle = raffles.findByIdForUpdate(raffleId)
                .filter(r -> callerIsAdmin || r.isOrganizer(callerId))
                .orElseThrow(NotFoundException::new);
        switch (raffle.getStatus()) {
            case DRAFT -> raffle.cancel();
            case OPEN -> {
                if (soldCount(raffle) > 0) {
                    throw new BusinessException("Não é possível cancelar uma rifa com números vendidos");
                }
                raffle.cancel();
            }
            default -> throw new BusinessException("Esta rifa não pode mais ser cancelada");
        }
        return toResponse(raffle);
    }

    /** Loads a raffle the caller may see: drafts only for their organizer. */
    @Transactional(readOnly = true)
    public Raffle getVisible(UUID raffleId, UUID callerOrNull) {
        return raffles.findById(raffleId)
                .filter(r -> r.getStatus() != RaffleStatus.DRAFT || r.isOrganizer(callerOrNull))
                .orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public RaffleResponse get(UUID raffleId, UUID callerOrNull) {
        return toResponse(getVisible(raffleId, callerOrNull));
    }

    @Transactional(readOnly = true)
    public PageResponse<RaffleResponse> listPublic(RaffleStatus status, String query, Pageable pageable) {
        Specification<Raffle> spec = (root, q, cb) -> cb.notEqual(root.get("status"), RaffleStatus.DRAFT);
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.strip().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), pattern));
        }
        return PageResponse.of(raffles.findAll(spec, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<RaffleResponse> listMine(UUID callerId, Pageable pageable) {
        Specification<Raffle> spec = (root, q, cb) -> cb.equal(root.get("organizer").get("id"), callerId);
        return PageResponse.of(raffles.findAll(spec, pageable), this::toResponse);
    }

    private void validate(RaffleRequest request) {
        if (!request.drawAt().isAfter(clock.instant())) {
            throw new InvalidRequestException("drawAt", "A data do sorteio deve estar no futuro");
        }
        if (request.maxPerUser() > request.totalNumbers()) {
            throw new InvalidRequestException("maxPerUser",
                    "O limite por participante não pode passar da quantidade de números");
        }
    }

    private static void apply(Raffle raffle, RaffleRequest request) {
        raffle.edit(request.title().strip(), blankToNull(request.description()), request.prize().strip(),
                request.ticketPriceCents(), request.totalNumbers(), request.maxPerUser(), request.drawAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    long soldCount(Raffle raffle) {
        return raffle.getStatus() == RaffleStatus.DRAFT ? 0 : tickets.countByRaffleId(raffle.getId());
    }

    RaffleResponse toResponse(Raffle raffle) {
        long sold = soldCount(raffle);
        boolean drawn = raffle.getStatus() == RaffleStatus.DRAWN;
        return new RaffleResponse(
                raffle.getId(),
                raffle.getTitle(),
                raffle.getDescription(),
                raffle.getPrize(),
                raffle.getTicketPriceCents(),
                raffle.getTotalNumbers(),
                raffle.getMaxPerUser(),
                raffle.getDrawAt(),
                raffle.getStatus(),
                new Person(raffle.getOrganizer().getId(), raffle.getOrganizer().getName()),
                sold,
                raffle.getTotalNumbers() - sold,
                raffle.getSeedHash(),
                drawn ? raffle.getWinningNumber() : null,
                drawn ? raffle.getWinner().getFirstName() : null,
                raffle.getDrawnAt());
    }
}
