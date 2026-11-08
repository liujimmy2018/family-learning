package cn.family.learning.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Tokens() {}
    public static String random() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                int unsigned = digest[i] & 255;
                hex[i * 2] = digits[unsigned >>> 4];
                hex[i * 2 + 1] = digits[unsigned & 15];
            }
            return new String(hex);
        }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
