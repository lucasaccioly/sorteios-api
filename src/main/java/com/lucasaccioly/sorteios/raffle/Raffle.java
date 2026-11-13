package com.lucasaccioly.sorteios.raffle;

import com.lucasaccioly.sorteios.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raffles")
public class Raffle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id")
    private User organizer;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 200)
    private String prize;

    @Column(name = "ticket_price_cents", nullable = false)
    private long ticketPriceCents;

    @Column(name = "total_numbers", nullable = false)
    private int totalNumbers;

    @Column(name = "max_per_user", nullable = false)
    private int maxPerUser;

    @Column(name = "draw_at", nullable = false)
    private Instant drawAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RaffleStatus status;

    /** Secret until the draw; never serialized by the API before that. */
    private byte[] seed;

    @Column(name = "seed_hash", length = 64)
    private String seedHash;

    @Column(name = "tickets_digest", length = 64)
    private String ticketsDigest;

    @Column(name = "winning_number")
    private Integer winningNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "drawn_at")
    private Instant drawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Raffle() {
    }

    public Raffle(User organizer, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.organizer = organizer;
        this.status = RaffleStatus.DRAFT;
        this.createdAt = createdAt;
    }

    public void edit(String title, String description, String prize, long ticketPriceCents,
                     int totalNumbers, int maxPerUser, Instant drawAt) {
        this.title = title;
        this.description = description;
        this.prize = prize;
        this.ticketPriceCents = ticketPriceCents;
        this.totalNumbers = totalNumbers;
        this.maxPerUser = maxPerUser;
        this.drawAt = drawAt;
    }

    public void open(byte[] seed, String seedHash) {
        this.seed = seed;
        this.seedHash = seedHash;
        this.status = RaffleStatus.OPEN;
    }

    public void cancel() {
        this.status = RaffleStatus.CANCELLED;
    }

    public void closeWithoutSales(Instant at) {
        this.status = RaffleStatus.CLOSED_NO_SALES;
        this.drawnAt = at;
    }

    public void drawn(String ticketsDigest, int winningNumber, User winner, Instant at) {
        this.ticketsDigest = ticketsDigest;
        this.winningNumber = winningNumber;
        this.winner = winner;
        this.drawnAt = at;
        this.status = RaffleStatus.DRAWN;
    }

    public boolean isOrganizer(UUID userId) {
        return organizer.getId().equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public User getOrganizer() {
        return organizer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPrize() {
        return prize;
    }

    public long getTicketPriceCents() {
        return ticketPriceCents;
    }

    public int getTotalNumbers() {
        return totalNumbers;
    }

    public int getMaxPerUser() {
        return maxPerUser;
    }

    public Instant getDrawAt() {
        return drawAt;
    }

    /** Only used by the demo seeder to fast-forward a raffle into the past. */
    public void setDrawAt(Instant drawAt) {
        this.drawAt = drawAt;
    }

    public RaffleStatus getStatus() {
        return status;
    }

    public byte[] getSeed() {
        return seed;
    }

    public String getSeedHash() {
        return seedHash;
    }

    public String getTicketsDigest() {
        return ticketsDigest;
    }

    public Integer getWinningNumber() {
        return winningNumber;
    }

    public User getWinner() {
        return winner;
    }

    public Instant getDrawnAt() {
        return drawnAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
