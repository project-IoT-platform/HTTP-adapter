package org.example.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CipherDemo {

    public static void main(String[] args) throws Exception {
        AesGcmCipher cipher = new AesGcmCipher(Base64.getDecoder().decode(AesGcmCipher.generateKey()));

        byte[] message = "{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8);

        String onTheWire = Base64.getEncoder().encodeToString(cipher.encrypt(message));
        System.out.println("encrypted (Base64): " + onTheWire);

        byte[] restored = cipher.decrypt(Base64.getDecoder().decode(onTheWire));
        System.out.println("decrypted:          " + new String(restored, StandardCharsets.UTF_8));
    }
}
