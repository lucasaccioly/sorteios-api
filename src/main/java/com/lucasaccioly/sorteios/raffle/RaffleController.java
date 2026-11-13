package com.lucasaccioly.sorteios.raffle;

import com.lucasaccioly.sorteios.auth.CurrentUser;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.PageResponse;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleRequest;
import com.lucasaccioly.sorteios.raffle.RaffleDtos.RaffleResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/raffles")
class RaffleController {

    private final RaffleService raffleService;

    RaffleController(RaffleService raffleService) {
        this.raffleService = raffleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RaffleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RaffleRequest request) {
        return raffleService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    RaffleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody RaffleRequest request) {
        return raffleService.update(id, CurrentUser.id(jwt), request);
    }

    @PostMapping("/{id}/publish")
    RaffleResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return raffleService.publish(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/cancel")
    RaffleResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return raffleService.cancel(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
    }

    @GetMapping("/{id}")
    RaffleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return raffleService.get(id, CurrentUser.idOrNull(jwt));
    }

    @GetMapping
    PageResponse<RaffleResponse> list(@RequestParam(required = false) RaffleStatus status,
                                      @RequestParam(required = false) String q,
                                      @PageableDefault(size = 20, sort = "drawAt") Pageable pageable) {
        return raffleService.listPublic(status, q, pageable);
    }

    @GetMapping("/mine")
    PageResponse<RaffleResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return raffleService.listMine(CurrentUser.id(jwt), pageable);
    }
}
