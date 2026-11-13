package com.lucasaccioly.sorteios.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    long countByRaffleId(UUID raffleId);

    long countByRaffleIdAndUserId(UUID raffleId, UUID userId);

    List<Ticket> findByRaffleIdOrderByNumberAsc(UUID raffleId);

    @Query("select t.number from Ticket t where t.raffle.id = :raffleId")
    Set<Integer> findNumbersByRaffleId(@Param("raffleId") UUID raffleId);

    @Query("select t from Ticket t join fetch t.raffle where t.user.id = :userId order by t.purchasedAt desc, t.number")
    List<Ticket> findMine(@Param("userId") UUID userId);
}
