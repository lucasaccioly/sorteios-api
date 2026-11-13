package com.lucasaccioly.sorteios.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** The authenticated user's id (JWT subject). */
    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Same as {@link #id(Jwt)}, but null for anonymous callers on public endpoints. */
    public static UUID idOrNull(Jwt jwt) {
        return jwt == null ? null : id(jwt);
    }
}
