package org.example.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumTest {

    @Test
    void knownCrc32Vector() {
        // стандартный тестовый вектор CRC-32: "123456789" -> cbf43926
        assertEquals("cbf43926", Checksum.crc32Hex("123456789".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void emptyInputGivesZeroCrcPaddedTo8Chars() {
        assertEquals("00000000", Checksum.crc32Hex(new byte[0]));
    }

    @Test
    void matchesIgnoresCaseAndSurroundingSpaces() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String crc = Checksum.crc32Hex(data);
        assertTrue(Checksum.matches(data, crc));
        assertTrue(Checksum.matches(data, crc.toUpperCase()));
        assertTrue(Checksum.matches(data, " " + crc + " "));
    }

    @Test
    void singleFlippedBitIsDetected() {
        byte[] data = "{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8);
        String crc = Checksum.crc32Hex(data);
        data[3] ^= 1;
        assertFalse(Checksum.matches(data, crc));
    }

    @Test
    void nullExpectedDoesNotMatch() {
        assertFalse(Checksum.matches(new byte[]{1}, null));
    }
}