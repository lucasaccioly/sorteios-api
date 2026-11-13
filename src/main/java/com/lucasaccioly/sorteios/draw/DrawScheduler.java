package com.lucasaccioly.sorteios.draw;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Draws due raffles every minute, and once at startup: the free hosting sleeps when idle, so raffles that
 * came due while it slept are caught up on wake (reads of a single raffle also draw it lazily).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class DrawScheduler implements ApplicationRunner {

    private final DrawService drawService;

    DrawScheduler(DrawService drawService) {
        this.drawService = drawService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void tick() {
        drawService.drawAllDue();
    }

    @Override
    public void run(ApplicationArguments args) {
        drawService.drawAllDue();
    }
}
