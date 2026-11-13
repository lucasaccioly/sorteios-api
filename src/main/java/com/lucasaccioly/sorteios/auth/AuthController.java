package com.lucasaccioly.sorteios.auth;

import com.lucasaccioly.sorteios.auth.AuthDtos.AuthResponse;
import com.lucasaccioly.sorteios.auth.AuthDtos.LoginRequest;
import com.lucasaccioly.sorteios.auth.AuthDtos.RegisterRequest;
import com.lucasaccioly.sorteios.auth.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Cadastro, login e usuário atual")
@RestController
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Cria uma conta e devolve o token JWT")
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Faz login e devolve o token JWT (24h)")
    @PostMapping("/api/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Dados do usuário autenticado")
    @GetMapping("/api/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(CurrentUser.id(jwt));
    }
}
