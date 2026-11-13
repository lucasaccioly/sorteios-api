package com.lucasaccioly.sorteios.demo;

import com.lucasaccioly.sorteios.auth.User;
import com.lucasaccioly.sorteios.auth.UserRepository;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleRequest;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleResponse;
import com.lucasaccioly.sorteios.raffle.RaffleRepository;
import com.lucasaccioly.sorteios.raffle.RaffleService;
import com.lucasaccioly.sorteios.raffle.RaffleStatus;
import com.lucasaccioly.sorteios.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Keeps the public demo usable: there is always an OPEN raffle with free numbers and time left to buy. */
@Component
@Profile("demo")
public class DemoMaintenance {

    static final String ORGANIZER_EMAIL = "organizador@demo.dev";
    static final int MIN_FREE_NUMBERS = 10;
    static final Duration MIN_TIME_LEFT = Duration.ofHours(1);
    static final Duration NEW_RAFFLE_DURATION = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(DemoMaintenance.class);

    record Prize(String title, String description, String prize) {
    }

    private static final List<Prize> PRIZES = List.of(
            new Prize("Rifa do Fone Bluetooth", "Fone sem fio com cancelamento de ruído.", "Fone de ouvido Bluetooth"),
            new Prize("Rifa da Cafeteira", "Cafeteira de cápsulas, nova na caixa.", "Cafeteira expresso"),
            new Prize("Rifa do Kindle", "Leitor digital com luz embutida.", "Kindle"),
            new Prize("Rifa da Bicicleta", "Bicicleta aro 29, 21 marchas.", "Bicicleta aro 29"),
            new Prize("Rifa do Smartwatch", "Relógio inteligente com GPS.", "Smartwatch"));

    private final UserRepository users;
    private final RaffleRepository raffles;
    private final TicketRepository tickets;
    private final RaffleService raffleService;
    private final Clock clock;

    DemoMaintenance(UserRepository users, RaffleRepository raffles, TicketRepository tickets,
                    RaffleService raffleService, Clock clock) {
        this.users = users;
        this.raffles = raffles;
        this.tickets = tickets;
        this.raffleService = raffleService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    void scheduled() {
        ensureOpenRaffle();
    }

    /** Creates and publishes a new demo raffle when none is open with enough free numbers and time; true if created. */
    public boolean ensureOpenRaffle() {
        UUID organizer = users.findByEmail(ORGANIZER_EMAIL).map(User::getId).orElse(null);
        if (organizer == null) {
            return false;
        }
        Instant now = clock.instant();
        boolean usable = raffles.findByOrganizerIdAndStatusAndDrawAtGreaterThanEqual(
                        organizer, RaffleStatus.OPEN, now.plus(MIN_TIME_LEFT)).stream()
                .anyMatch(r -> r.getTotalNumbers() - tickets.countByRaffleId(r.getId()) >= MIN_FREE_NUMBERS);
        if (usable) {
            return false;
        }
        Prize prize = PRIZES.get((int) (raffles.countByOrganizerId(organizer) % PRIZES.size()));
        RaffleResponse created = raffleService.create(organizer, new RaffleRequest(prize.title(),
                prize.description() + " Rifa de demonstração: compre números à vontade.", prize.prize(),
                1000L, 100, 10, now.plus(NEW_RAFFLE_DURATION)));
        raffleService.publish(created.id(), organizer);
        log.info("Demo: opened raffle {} ({})", created.id(), prize.title());
        return true;
    }
}
