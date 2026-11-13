package com.lucasaccioly.sorteios.draw;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SeedsTest {

    @Test
    void newSeed_has32RandomBytes() {
        byte[] a = Seeds.newSeed();
        byte[] b = Seeds.newSeed();
        assertThat(a).hasSize(32);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        assertThat(Seeds.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hex_isLowercase() {
        assertThat(Seeds.hex(new byte[]{0x0A, (byte) 0xFF})).isEqualTo("0aff");
    }
}
