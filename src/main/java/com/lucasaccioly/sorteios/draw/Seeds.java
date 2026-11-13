package com.lucasaccioly.sorteios.draw;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Seed generation and hashing for the commit-reveal draw. */
public final class Seeds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Seeds() {
    }

    public static byte[] newSeed() {
        byte[] seed = new byte[32];
        RANDOM.nextBytes(seed);
        return seed;
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return hex(sha256(data));
    }

    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }
}
