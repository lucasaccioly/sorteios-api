package com.lucasaccioly.sorteios.raffle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaffleRepository extends JpaRepository<Raffle, UUID>, JpaSpecificationExecutor<Raffle> {

    /** Row lock (SELECT ... FOR UPDATE): serializes purchases and the draw of the same raffle. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Raffle r where r.id = :id")
    Optional<Raffle> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByOrganizerId(UUID organizerId);

    long countByOrganizerId(UUID organizerId);

    List<Raffle> findByOrganizerIdAndStatusAndDrawAtGreaterThanEqual(UUID organizerId, RaffleStatus status, Instant drawAt);

    @Query("select r.id from Raffle r where r.status = com.lucasaccioly.sorteios.raffle.RaffleStatus.OPEN and r.drawAt <= :now")
    List<UUID> findIdsDue(@Param("now") Instant now);

    @Query("""
            select count(r) > 0 from Raffle r
            where r.id = :id and r.status = com.lucasaccioly.sorteios.raffle.RaffleStatus.OPEN and r.drawAt <= :now""")
    boolean isDue(@Param("id") UUID id, @Param("now") Instant now);
}
