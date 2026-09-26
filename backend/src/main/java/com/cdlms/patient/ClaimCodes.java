package com.cdlms.patient;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * One-time registration codes (ADR-018). The front desk prints the code on the patient's slip;
 * the patient enters it when creating their login, which links the login to the existing record.
 *
 * <p>Codes are 10 characters from an alphabet without look-alikes (no 0/O, 1/I/L), about 49 bits
 * of randomness, shown as {@code ABCDE-FGH23}. Only a SHA-256 hash is stored.
 */
@Component
public class ClaimCodes {

    public static final Duration VALIDITY = Duration.ofDays(30);
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Case, spaces and dashes don't matter when the patient types the code. */
    public static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
