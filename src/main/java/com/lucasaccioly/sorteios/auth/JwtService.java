package com.lucasaccioly.sorteios.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Issues HS256 access tokens. Validation lives in {@link SecurityConfig}. */
@Service
public class JwtService {

    static final Duration TTL = Duration.ofHours(24);

    private final JwtEncoder encoder;
    private final Clock clock;

    JwtService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
