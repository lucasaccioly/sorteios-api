package com.lucasaccioly.sorteios.ticket;

import com.lucasaccioly.sorteios.auth.CurrentUser;
import com.lucasaccioly.sorteios.ticket.TicketDtos.MyTicket;
import com.lucasaccioly.sorteios.ticket.TicketDtos.NumbersResponse;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseRequest;
import com.lucasaccioly.sorteios.ticket.TicketDtos.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Números", description = "Compra e consulta de números")
@RestController
class TicketController {

    private final TicketService ticketService;

    TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "Compra números escolhidos ({\"numbers\": [..]}) ou aleatórios ({\"quantity\": n})")
    @PostMapping("/api/raffles/{id}/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseResponse purchase(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @RequestBody PurchaseRequest request) {
        return ticketService.purchase(id, CurrentUser.id(jwt), request);
    }

    @Operation(summary = "Números vendidos e livres da rifa")
    @GetMapping("/api/raffles/{id}/numbers")
    NumbersResponse numbers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ticketService.numbers(id, CurrentUser.idOrNull(jwt));
    }

    @Operation(summary = "Meus números em todas as rifas")
    @GetMapping("/api/tickets/mine")
    List<MyTicket> mine(@AuthenticationPrincipal Jwt jwt) {
        return ticketService.mine(CurrentUser.id(jwt));
    }
}
