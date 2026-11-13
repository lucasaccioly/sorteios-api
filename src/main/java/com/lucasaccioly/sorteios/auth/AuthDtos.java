package com.lucasaccioly.sorteios.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe o nome") @Size(max = 100, message = "Nome muito longo") String name,
            @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") @Size(max = 254) String email,
            @NotBlank(message = "Informe a senha")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String password) {
        public RegisterRequest {
            email = email == null ? null : email.strip();
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail") String email,
            @NotBlank(message = "Informe a senha") String password) {
        public LoginRequest {
            email = email == null ? null : email.strip();
        }
    }

    public record UserResponse(UUID id, String name, String email, Role role) {
        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
