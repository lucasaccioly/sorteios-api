package com.lucasaccioly.sorteios.demo;

import com.lucasaccioly.sorteios.auth.Role;
import com.lucasaccioly.sorteios.auth.User;
import com.lucasaccioly.sorteios.auth.UserRepository;
import com.lucasaccioly.sorteios.draw.DrawService;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleRequest;
import com.lucasaccioly.sorteios.raffle.RaffleRepository;
import com.lucasaccioly.sorteios.raffle.RaffleService;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseRequest;
import com.lucasaccioly.sorteios.ticket.TicketService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Demo profile: public demo accounts plus one raffle in each state (including a drawn one with a verifiable
 * proof). Idempotent, so every restart of the free instance just tops up what is missing.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoSeeder implements ApplicationRunner {

    /** Published in the README and in the Swagger description: these are throwaway demo accounts. */
    public static final String DEMO_PASSWORD = "demo12345";

    private final UserRepository users;
    private final RaffleRepository raffles;
    private final RaffleService raffleService;
    private final TicketService ticketService;
    private final DrawService drawService;
    private final DemoMaintenance maintenance;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate tx;

    DemoSeeder(UserRepository users, RaffleRepository raffles, RaffleService raffleService,
               TicketService ticketService, DrawService drawService, DemoMaintenance maintenance,
               PasswordEncoder passwordEncoder, Clock clock, PlatformTransactionManager txManager) {
        this.users = users;
        this.raffles = raffles;
        this.raffleService = raffleService;
        this.ticketService = ticketService;
        this.drawService = drawService;
        this.maintenance = maintenance;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID organizer = ensureUser("Organizadora Demo", DemoMaintenance.ORGANIZER_EMAIL);
        ensureUser("Participante Demo", DemoMaintenance.PARTICIPANT_EMAIL);
        List<UUID> buyers = List.of(
                ensureUser("Ana Bot", "bot1@demo.dev"),
                ensureUser("Bruno Bot", "bot2@demo.dev"),
                ensureUser("Carla Bot", "bot3@demo.dev"));
        if (!raffles.existsByOrganizerId(organizer)) {
            seedShowcase(organizer, buyers);
        }
        maintenance.ensureOpenRaffle();
    }

    private UUID ensureUser(String name, String email) {
        return users.findByEmail(email).map(User::getId).orElseGet(() -> users.save(
                new User(name, email, passwordEncoder.encode(DEMO_PASSWORD), Role.USER, clock.instant())).getId());
    }

    private void seedShowcase(UUID organizer, List<UUID> buyers) {
        UUID drawn = createOpen(organizer, "Rifa do Videogame", "Console de última geração. Já sorteada: confira a prova!",
                "Videogame", 50);
        for (UUID buyer : buyers) {
            ticketService.purchase(drawn, buyer, new PurchaseRequest(null, 5));
        }
        moveDrawToPast(drawn);
        drawService.drawIfDue(drawn);

        UUID empty = createOpen(organizer, "Rifa do Livro Autografado", "Ninguém comprou a tempo.", "Livro autografado", 20);
        moveDrawToPast(empty);
        drawService.drawIfDue(empty);

        UUID cancelled = createOpen(organizer, "Rifa da Viagem", "Cancelada pela organizadora antes das vendas.",
                "Fim de semana na serra", 100);
        raffleService.cancel(cancelled, organizer, false);

        raffleService.create(organizer, request("Rifa do Notebook (rascunho)", "Ainda em preparação.", "Notebook", 200));
    }

    private UUID createOpen(UUID organizer, String title, String description, String prize, int totalNumbers) {
        UUID id = raffleService.create(organizer, request(title, description, prize, totalNumbers)).id();
        raffleService.publish(id, organizer);
        return id;
    }

    private RaffleRequest request(String title, String description, String prize, int totalNumbers) {
        return new RaffleRequest(title, description, prize, 500L, totalNumbers, 5,
                clock.instant().plus(Duration.ofHours(1)));
    }

    private void moveDrawToPast(UUID raffleId) {
        tx.executeWithoutResult(status -> raffles.findById(raffleId)
                .ifPresent(r -> r.setDrawAt(clock.instant().minusSeconds(60))));
    }
}
