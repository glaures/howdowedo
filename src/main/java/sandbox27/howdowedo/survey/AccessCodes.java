package sandbox27.howdowedo.survey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates high-entropy access codes and hashes them for storage.
 *
 * <p>Codes are random bearer tokens, so a fast cryptographic hash (SHA-256) is sufficient and
 * appropriate - we only ever store the hash, never the code itself.
 */
public final class AccessCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private AccessCodes() {
    }

    /** A new, unguessable code (~192 bits) safe to place in a URL. */
    public static String generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Hex-encoded SHA-256 of the code, as stored in {@link SurveyAccessCode}. */
    public static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
