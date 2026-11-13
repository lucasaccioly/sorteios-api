package com.lucasaccioly.sorteios.raffle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class RaffleDtos {

    private RaffleDtos() {
    }

    public record RaffleRequest(
            @NotBlank(message = "Informe o título") @Size(max = 120, message = "Título muito longo") String title,
            @Size(max = 2000, message = "Descrição muito longa") String description,
            @NotBlank(message = "Informe o prêmio") @Size(max = 200, message = "Prêmio muito longo") String prize,
            @NotNull(message = "Informe o preço") @PositiveOrZero(message = "O preço não pode ser negativo")
            Long ticketPriceCents,
            @NotNull(message = "Informe a quantidade de números")
            @Min(value = 10, message = "Mínimo de 10 números") @Max(value = 10000, message = "Máximo de 10.000 números")
            Integer totalNumbers,
            @NotNull(message = "Informe o limite por participante") @Min(value = 1, message = "Mínimo de 1 número")
            Integer maxPerUser,
            @NotNull(message = "Informe a data do sorteio") Instant drawAt) {
    }

    public record Person(UUID id, String name) {
    }

    public record RaffleResponse(
            UUID id,
            String title,
            String description,
            String prize,
            long ticketPriceCents,
            int totalNumbers,
            int maxPerUser,
            Instant drawAt,
            RaffleStatus status,
            Person organizer,
            long soldCount,
            long availableCount,
            String seedHash,
            Integer winningNumber,
            String winnerName,
            Instant drawnAt) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
