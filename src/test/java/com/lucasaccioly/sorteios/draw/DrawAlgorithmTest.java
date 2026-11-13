package com.lucasaccioly.sorteios.draw;

import com.lucasaccioly.sorteios.draw.DrawAlgorithm.SoldTicket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DrawAlgorithmTest {

    private static final byte[] SEED = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            SEED[i] = (byte) i;
        }
    }

    private static final List<SoldTicket> TICKETS = List.of(
            new SoldTicket(3, UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new SoldTicket(7, UUID.fromString("00000000-0000-0000-0000-000000000002")),
            new SoldTicket(9, UUID.fromString("00000000-0000-0000-0000-000000000003")));

    // Expected values computed independently with Python's hashlib (same formula as the README).
    @Test
    void fixedVector_matchesIndependentImplementation() {
        String line = DrawAlgorithm.ticketsLine(TICKETS);
        assertThat(line).isEqualTo("3:00000000-0000-0000-0000-000000000001,"
                + "7:00000000-0000-0000-0000-000000000002,9:00000000-0000-0000-0000-000000000003");
        String digest = DrawAlgorithm.digest(line);
        assertThat(digest).isEqualTo("89abcb012caa7b88871a8580fe5fbdcf06357acde0ae24da3b50365bfb8ecf38");
        // h[0..8) = 11614212128543738087 > Long.MAX_VALUE, so this also proves the unsigned arithmetic.
        assertThat(DrawAlgorithm.winnerIndex(SEED, digest, 3)).isEqualTo(2);
        assertThat(Seeds.sha256Hex(SEED)).isEqualTo("630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd");
    }

    @Test
    void ticketsLine_sortsByNumber() {
        List<SoldTicket> shuffled = List.of(TICKETS.get(2), TICKETS.get(0), TICKETS.get(1));
        assertThat(DrawAlgorithm.ticketsLine(shuffled)).isEqualTo(DrawAlgorithm.ticketsLine(TICKETS));
    }

    @Test
    void winnerIndex_alwaysInRange() {
        for (int i = 0; i < 1000; i++) {
            int sold = 1 + (i % 97);
            int index = DrawAlgorithm.winnerIndex(Seeds.newSeed(), "abc" + i, sold);
            assertThat(index).isBetween(0, sold - 1);
        }
    }
}
