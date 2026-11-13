package com.lucasaccioly.sorteios.draw;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Commit-reveal draw. Public contract (scripts/verify_draw.py implements the same steps):
 * <ol>
 *   <li>ticketsLine = "number:userId" of every sold ticket, ordered by number, joined by ","</li>
 *   <li>ticketsDigest = hex(SHA-256(utf8(ticketsLine)))</li>
 *   <li>h = SHA-256(seed || utf8(ticketsDigest)); x = first 8 bytes of h as unsigned big-endian</li>
 *   <li>winnerIndex = x mod soldCount; the winner is the ticket at that position</li>
 * </ol>
 */
public final class DrawAlgorithm {

    public static final String DESCRIPTION = """
            1) Antes das vendas foi publicado seedHash = SHA-256(seed). \
            2) ticketsLine = "numero:userId" de cada bilhete vendido, em ordem crescente de número, separados por vírgula. \
            3) ticketsDigest = hex(SHA-256(ticketsLine em UTF-8)). \
            4) h = SHA-256(bytes da seed + ticketsDigest em UTF-8); x = primeiros 8 bytes de h como inteiro sem sinal (big-endian). \
            5) winnerIndex = x mod quantidade de bilhetes; o vencedor é o bilhete nessa posição.""";

    public record SoldTicket(int number, UUID userId) {
    }

    private DrawAlgorithm() {
    }

    public static String ticketsLine(List<SoldTicket> tickets) {
        return tickets.stream()
                .sorted(Comparator.comparingInt(SoldTicket::number))
                .map(t -> t.number() + ":" + t.userId())
                .collect(Collectors.joining(","));
    }

    public static String digest(String ticketsLine) {
        return Seeds.sha256Hex(ticketsLine.getBytes(StandardCharsets.UTF_8));
    }

    public static int winnerIndex(byte[] seed, String ticketsDigest, int soldCount) {
        if (soldCount < 1) {
            throw new IllegalArgumentException("soldCount must be positive");
        }
        byte[] digestBytes = ticketsDigest.getBytes(StandardCharsets.UTF_8);
        byte[] input = ByteBuffer.allocate(seed.length + digestBytes.length).put(seed).put(digestBytes).array();
        long x = ByteBuffer.wrap(Seeds.sha256(input), 0, 8).getLong();
        return (int) Long.remainderUnsigned(x, soldCount);
    }
}
