package com.aierp.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** AES-GCM vault. The key is read only from the server environment and is never logged. */
@Component
public final class GoogleTokenVault {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public GoogleTokenVault(Environment env) {
        String raw = env.getProperty("GOOGLE_TOKEN_ENCRYPTION_KEY", "");
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(raw); } catch (IllegalArgumentException e) { decoded = new byte[0]; }
        key = decoded.length == 32 ? decoded : null;
    }
    public boolean configured() { return key != null; }
    public String encrypt(String value, java.util.UUID userId, String subject) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, subject));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (Exception e) { throw new IllegalStateException("TOKEN_VAULT_FAILURE", e); }
    }
    public String decrypt(String value, java.util.UUID userId, String subject) {
        requireKey();
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(userId, subject));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("TOKEN_VAULT_FAILURE", e); }
    }
    private byte[] aad(java.util.UUID userId, String subject) { return (userId + "\u0000" + subject).getBytes(StandardCharsets.UTF_8); }
    private void requireKey() { if (key == null) throw new IllegalStateException("GOOGLE_TOKEN_KEY_CONFIGURATION_REQUIRED"); }
}
