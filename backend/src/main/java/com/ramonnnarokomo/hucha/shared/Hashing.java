package com.ramonnnarokomo.hucha.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Comparación en tiempo constante para no filtrar información por tiempos de respuesta. */
    public static boolean matches(String expectedHash, String value) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                sha256(value).getBytes(StandardCharsets.UTF_8));
    }
}
