package org.example.codec;

import java.nio.charset.StandardCharsets;
public final class Aad {

    private Aad() {
    }

    public static byte[] build(String direction, String deviceId, long messageId, String timestamp) {
        return (direction + "|" + deviceId + "|" + messageId + "|" + timestamp).getBytes(StandardCharsets.UTF_8);
    }
}
