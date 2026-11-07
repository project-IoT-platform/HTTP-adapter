package org.example.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.CRC32;

public final class Checksum {

    private Checksum() {
    }

    public static String crc32Hex(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return String.format("%08x", crc.getValue());
    }
    public static boolean matches(byte[] data, String expectedHex) {
        if (expectedHex == null) {
            return false;
        }
        byte[] expected = expectedHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actual = crc32Hex(data).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}