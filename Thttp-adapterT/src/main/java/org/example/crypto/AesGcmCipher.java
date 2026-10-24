package org.example.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmCipher {

    private static final int KEY_BYTES = 32;   // 256 бит
    private static final int IV_BYTES = 12;    // рекомендуемый размер для GCM
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(byte[] keyBytes) {
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be exactly 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public static AesGcmCipher fromEnv() {
        String value = System.getenv("IOT_AES_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("IOT_AES_KEY is not set (Base64 of 32 bytes)");
        }
        return new AesGcmCipher(Base64.getDecoder().decode(value.trim()));
    }
    public static String generateKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
    }

    public byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);

        byte[] result = new byte[IV_BYTES + encrypted.length];
        System.arraycopy(iv, 0, result, 0, IV_BYTES);
        System.arraycopy(encrypted, 0, result, IV_BYTES, encrypted.length);
        return result;
    }

    public byte[] decrypt(byte[] data) throws GeneralSecurityException {
        if (data.length < IV_BYTES + TAG_BITS / 8) {
            throw new GeneralSecurityException("message is too short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
        return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
    }
}
