package com.lucasaccioly.sorteios.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Creates (or promotes) the admin account from ADMIN_EMAIL / ADMIN_PASSWORD when both are set. Idempotent. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
                   @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = AuthService.normalizeEmail(email);
        users.findByEmail(normalized).ifPresentOrElse(
                user -> user.setRole(Role.ADMIN),
                () -> users.save(new User("Administrador", normalized, passwordEncoder.encode(password),
                        Role.ADMIN, clock.instant())));
        log.info("Admin account ready: {}", normalized);
    }
}
