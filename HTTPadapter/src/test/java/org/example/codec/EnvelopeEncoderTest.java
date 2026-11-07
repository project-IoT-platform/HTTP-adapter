package org.example.codec;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.example.crypto.AesGcmCipher;
import org.example.model.Envelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeEncoderTest {

    private AesGcmCipher cipher;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        cipher = new AesGcmCipher(Base64.getDecoder().decode(AesGcmCipher.generateKey()));
        // тот же порядок, что в HttpAdapter: encoder, затем decoder
        channel = new EmbeddedChannel(
                new EnvelopeEncoder(cipher),
                new EnvelopeDecoder(cipher, new ReplayGuard(Duration.ofMinutes(10))));
    }

    private FullHttpResponse response(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED, Unpooled.wrappedBuffer(bytes));
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return resp;
    }

    @Test
    void responseWithoutRequestContextIsNotWrapped() {
        channel.writeOutbound(response("plain"));
        FullHttpResponse out = channel.readOutbound();
        assertEquals("plain", out.content().toString(StandardCharsets.UTF_8));
        out.release();
    }

    @Test
    void responseToDecodedRequestIsEncryptedWithValidChecksum() throws Exception {
        long id = System.currentTimeMillis();
        String ts = Instant.now().toString();
        byte[] enc = cipher.encrypt("{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8),
                Aad.build("req", "sensor-1", id, ts));
        Envelope in = new Envelope(id, ts, Base64.getEncoder().encodeToString(enc), Checksum.crc32Hex(enc));

        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/devices/sensor-1/telemetry",
                Unpooled.wrappedBuffer(EnvelopeDecoder.MAPPER.writeValueAsBytes(in)));
        req.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        channel.writeInbound(req);
        ((FullHttpRequest) channel.readInbound()).release();

        channel.writeOutbound(response("ok"));
        FullHttpResponse out = channel.readOutbound();
        try {
            Envelope ack = EnvelopeDecoder.MAPPER.readValue(
                    out.content().toString(StandardCharsets.UTF_8), Envelope.class);
            assertEquals(id, ack.messageId());

            byte[] encryptedAck = Base64.getDecoder().decode(ack.payload());
            assertTrue(Checksum.matches(encryptedAck, ack.checksum()), "CRC ответа должен совпадать");

            byte[] aad = Aad.build("resp", "sensor-1", ack.messageId(), ack.timestamp());
            assertEquals("ok", new String(cipher.decrypt(encryptedAck, aad), StandardCharsets.UTF_8));
        } finally {
            out.release();
        }
    }
}