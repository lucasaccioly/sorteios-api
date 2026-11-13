package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.auth.User;
import com.lucasaccioly.sorteios.raffle.Raffle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raffle_id")
    private Raffle raffle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int number;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    protected Ticket() {
    }

    public Ticket(Raffle raffle, User user, int number, Instant purchasedAt) {
        this.id = UUID.randomUUID();
        this.raffle = raffle;
        this.user = user;
        this.number = number;
        this.purchasedAt = purchasedAt;
    }

    public UUID getId() {
        return id;
    }

    public Raffle getRaffle() {
        return raffle;
    }

    public User getUser() {
        return user;
    }

    public int getNumber() {
        return number;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}
