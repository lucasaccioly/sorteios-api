package com.lucasaccioly.sorteios.auth;

import com.lucasaccioly.sorteios.auth.AuthDtos.AuthResponse;
import com.lucasaccioly.sorteios.auth.AuthDtos.LoginRequest;
import com.lucasaccioly.sorteios.auth.AuthDtos.RegisterRequest;
import com.lucasaccioly.sorteios.auth.AuthDtos.UserResponse;
import com.lucasaccioly.sorteios.common.BusinessException;
import com.lucasaccioly.sorteios.common.InvalidRequestException;
import com.lucasaccioly.sorteios.common.NotFoundException;
import com.lucasaccioly.sorteios.common.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) { // BCrypt limit is bytes, not chars
            throw new InvalidRequestException("password", "Senha muito longa");
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new BusinessException("E-mail já cadastrado");
        }
        User user = users.save(new User(request.name().strip(), email,
                passwordEncoder.encode(request.password()), Role.USER, clock.instant()));
        return new AuthResponse(jwtService.issue(user), UserResponse.of(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha inválidos"));
        return new AuthResponse(jwtService.issue(user), UserResponse.of(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::of).orElseThrow(NotFoundException::new);
    }
}
