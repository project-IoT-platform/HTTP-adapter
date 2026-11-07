package org.example.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCipherTest {

    private static AesGcmCipher newCipher() throws Exception {
        return new AesGcmCipher(Base64.getDecoder().decode(AesGcmCipher.generateKey()));
    }

    private static final byte[] AAD = "req|sensor-1|1|2026-10-31T12:00:00Z".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAIN = "{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTrip() throws Exception {
        AesGcmCipher c = newCipher();
        assertArrayEquals(PLAIN, c.decrypt(c.encrypt(PLAIN, AAD), AAD));
    }

    @Test
    void sameInputGivesDifferentCiphertext() throws Exception {
        AesGcmCipher c = newCipher();
        assertFalse(java.util.Arrays.equals(c.encrypt(PLAIN, AAD), c.encrypt(PLAIN, AAD)));
    }

    @Test
    void flippedBitInCiphertextIsRejected() throws Exception {
        AesGcmCipher c = newCipher();
        byte[] enc = c.encrypt(PLAIN, AAD);
        enc[enc.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> c.decrypt(enc, AAD));
    }

    @Test
    void differentAadIsRejected() throws Exception {
        AesGcmCipher c = newCipher();
        byte[] enc = c.encrypt(PLAIN, AAD);
        byte[] other = "req|sensor-1|2|2026-10-31T12:00:00Z".getBytes(StandardCharsets.UTF_8);
        assertThrows(GeneralSecurityException.class, () -> c.decrypt(enc, other));
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        byte[] enc = newCipher().encrypt(PLAIN, AAD);
        assertThrows(GeneralSecurityException.class, () -> newCipher().decrypt(enc, AAD));
    }

    @Test
    void tooShortInputIsRejected() throws Exception {
        AesGcmCipher c = newCipher();
        assertThrows(GeneralSecurityException.class, () -> c.decrypt(new byte[10], AAD));
    }

    @Test
    void keyOfWrongLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmCipher(new byte[16]));
    }
}