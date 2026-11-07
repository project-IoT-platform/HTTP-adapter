package org.example.codec;

import io.netty.buffer.ByteBuf;
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
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeDecoderTest {

    private static final String DEVICE = "sensor-1";
    private static final byte[] TELEMETRY = "{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8);

    private AesGcmCipher cipher;
    private ReplayGuard replayGuard;
    private EmbeddedChannel channel;
    private long nextId;

    @BeforeEach
    void setUp() throws Exception {
        cipher = new AesGcmCipher(Base64.getDecoder().decode(AesGcmCipher.generateKey()));
        replayGuard = new ReplayGuard(Duration.ofMinutes(10));
        channel = newChannel();
        nextId = System.currentTimeMillis();
    }

    private EmbeddedChannel newChannel() {
        return new EmbeddedChannel(new EnvelopeDecoder(cipher, replayGuard));
    }

    // ---------- helpers ----------

    private String json(Envelope e) throws Exception {
        return EnvelopeDecoder.MAPPER.writeValueAsString(e);
    }

    /** Корректный конверт для заданного messageId и timestamp. */
    private Envelope valid(long id, String ts, byte[] plain) throws Exception {
        byte[] enc = cipher.encrypt(plain, Aad.build("req", DEVICE, id, ts));
        return new Envelope(id, ts, Base64.getEncoder().encodeToString(enc), Checksum.crc32Hex(enc));
    }

    private Envelope valid() throws Exception {
        return valid(++nextId, Instant.now().toString(), TELEMETRY);
    }

    private FullHttpRequest post(String uri, String contentType, byte[] body) {
        FullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, uri, Unpooled.wrappedBuffer(body));
        if (contentType != null) {
            req.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        req.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return req;
    }

    private FullHttpRequest post(String body) {
        return post("/devices/" + DEVICE + "/telemetry", "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private FullHttpResponse send(FullHttpRequest req) {
        channel.writeInbound(req);
        return channel.readOutbound();
    }

    private void assertRejected(HttpResponseStatus status, String messagePart, FullHttpResponse resp) {
        assertNotNull(resp, "ожидали отказ " + status);
        try {
            assertEquals(status, resp.status());
            assertTrue(resp.content().toString(StandardCharsets.UTF_8).contains(messagePart),
                    "в теле ответа нет '" + messagePart + "': " + resp.content().toString(StandardCharsets.UTF_8));
            assertNull(channel.readInbound(), "отклонённое сообщение не должно уходить дальше по pipeline");
        } finally {
            resp.release();
        }
    }

    // ---------- happy path ----------

    @Test
    void validMessageIsDecryptedAndPassedOn() throws Exception {
        assertNull(send(post(json(valid()))));

        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        try {
            assertArrayEquals(TELEMETRY, ByteBufBytes.of(passed.content()));
            assertEquals(TELEMETRY.length, HttpUtil.getContentLength(passed));
        } finally {
            passed.release();
        }
        assertEquals(DEVICE, channel.attr(EnvelopeDecoder.CONTEXT).get().deviceId());
    }

    @Test
    void uppercaseChecksumIsAccepted() throws Exception {
        Envelope e = valid();
        Envelope upper = new Envelope(e.messageId(), e.timestamp(), e.payload(), e.checksum().toUpperCase());
        assertNull(send(post(json(upper))));
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        passed.release();
    }

    // ---------- маршрутизация: чужие запросы не трогаем ----------

    @Test
    void foreignPathIsPassedThroughUntouched() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        FullHttpRequest req = post("/health", "text/plain", body);
        assertNull(send(req));
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        assertArrayEquals(body, ByteBufBytes.of(passed.content()));
        passed.release();
    }

    @Test
    void getRequestIsPassedThrough() {
        FullHttpRequest get = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/devices/" + DEVICE + "/telemetry", Unpooled.EMPTY_BUFFER);
        assertNull(send(get));
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        passed.release();
    }

    // ---------- структура запроса ----------

    @Test
    void invalidDeviceIdIsRejected() throws Exception {
        FullHttpRequest req = post("/devices/bad$id/telemetry", "application/json",
                json(valid()).getBytes(StandardCharsets.UTF_8));
        assertRejected(HttpResponseStatus.BAD_REQUEST, "invalid device id", send(req));
    }

    @Test
    void wrongContentTypeIsRejected() throws Exception {
        FullHttpRequest req = post("/devices/" + DEVICE + "/telemetry", "text/plain",
                json(valid()).getBytes(StandardCharsets.UTF_8));
        assertRejected(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type", send(req));
    }

    @Test
    void missingContentTypeIsRejected() throws Exception {
        FullHttpRequest req = post("/devices/" + DEVICE + "/telemetry", null,
                json(valid()).getBytes(StandardCharsets.UTF_8));
        assertRejected(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type", send(req));
    }

    @Test
    void contentTypeWithCharsetIsAccepted() throws Exception {
        FullHttpRequest req = post("/devices/" + DEVICE + "/telemetry", "application/json; charset=UTF-8",
                json(valid()).getBytes(StandardCharsets.UTF_8));
        assertNull(send(req));
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        passed.release();
    }

    @Test
    void emptyBodyIsRejected() {
        assertRejected(HttpResponseStatus.BAD_REQUEST, "empty body", send(post("")));
    }

    @Test
    void garbageBodyIsRejected() {
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not a valid JSON envelope", send(post("{not json")));
    }

    @Test
    void truncatedJsonIsRejected() throws Exception {
        String full = json(valid());
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not a valid JSON envelope",
                send(post(full.substring(0, full.length() - 10))));
    }

    @Test
    void trailingGarbageAfterJsonIsRejected() throws Exception {
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not a valid JSON envelope",
                send(post(json(valid()) + "xxx")));
    }

    @Test
    void wrongFieldTypeIsRejected() {
        String body = "{\"messageId\":\"abc\",\"timestamp\":\"2026-10-31T12:00:00Z\",\"payload\":\"AAAA\",\"checksum\":\"00000000\"}";
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not a valid JSON envelope", send(post(body)));
    }

    // ---------- обязательные поля ----------

    @Test
    void missingChecksumIsRejected() throws Exception {
        Envelope e = valid();
        Envelope noCrc = new Envelope(e.messageId(), e.timestamp(), e.payload(), null);
        assertRejected(HttpResponseStatus.BAD_REQUEST, "required", send(post(json(noCrc))));
    }

    @Test
    void missingPayloadIsRejected() throws Exception {
        Envelope e = valid();
        Envelope noPayload = new Envelope(e.messageId(), e.timestamp(), null, e.checksum());
        assertRejected(HttpResponseStatus.BAD_REQUEST, "required", send(post(json(noPayload))));
    }

    @Test
    void missingMessageIdIsRejected() throws Exception {
        Envelope e = valid();
        Envelope noId = new Envelope(null, e.timestamp(), e.payload(), e.checksum());
        assertRejected(HttpResponseStatus.BAD_REQUEST, "required", send(post(json(noId))));
    }

    @Test
    void nonPositiveMessageIdIsRejected() throws Exception {
        Envelope e = valid(0, Instant.now().toString(), TELEMETRY);
        assertRejected(HttpResponseStatus.BAD_REQUEST, "messageId must be positive", send(post(json(e))));
    }

    @Test
    void emptyPayloadIsRejected() throws Exception {
        Envelope e = new Envelope(++nextId, Instant.now().toString(), "", "00000000");
        assertRejected(HttpResponseStatus.BAD_REQUEST, "payload is empty", send(post(json(e))));
    }

    @Test
    void malformedChecksumFormatIsRejected() throws Exception {
        Envelope e = valid();
        for (String bad : new String[]{"xyz", "1234567", "123456789", "gggggggg"}) {
            channel = newChannel();
            Envelope withBad = new Envelope(e.messageId(), e.timestamp(), e.payload(), bad);
            assertRejected(HttpResponseStatus.BAD_REQUEST, "checksum must be 8 hex", send(post(json(withBad))));
        }
    }

    // ---------- время ----------

    @Test
    void invalidTimestampFormatIsRejected() throws Exception {
        Envelope e = valid();
        Envelope bad = new Envelope(e.messageId(), "вчера", e.payload(), e.checksum());
        assertRejected(HttpResponseStatus.BAD_REQUEST, "ISO-8601", send(post(json(bad))));
    }

    @Test
    void oldTimestampIsRejected() throws Exception {
        String old = Instant.now().minus(Duration.ofMinutes(30)).toString();
        assertRejected(HttpResponseStatus.BAD_REQUEST, "out of the allowed window",
                send(post(json(valid(++nextId, old, TELEMETRY)))));
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        String future = Instant.now().plus(Duration.ofMinutes(30)).toString();
        assertRejected(HttpResponseStatus.BAD_REQUEST, "out of the allowed window",
                send(post(json(valid(++nextId, future, TELEMETRY)))));
    }

    // ---------- целостность: Base64 и контрольная сумма ----------

    @Test
    void invalidBase64IsRejected() throws Exception {
        Envelope e = new Envelope(++nextId, Instant.now().toString(), "это не base64!", "00000000");
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not valid Base64", send(post(json(e))));
    }

    @Test
    void flippedBitInPayloadFailsChecksum() throws Exception {
        long id = ++nextId;
        String ts = Instant.now().toString();
        byte[] enc = cipher.encrypt(TELEMETRY, Aad.build("req", DEVICE, id, ts));
        String crc = Checksum.crc32Hex(enc);
        enc[enc.length / 2] ^= 1;
        Envelope e = new Envelope(id, ts, Base64.getEncoder().encodeToString(enc), crc);
        assertRejected(HttpResponseStatus.UNPROCESSABLE_ENTITY, "checksum mismatch", send(post(json(e))));
    }

    @Test
    void truncatedPayloadFailsChecksum() throws Exception {
        long id = ++nextId;
        String ts = Instant.now().toString();
        byte[] enc = cipher.encrypt(TELEMETRY, Aad.build("req", DEVICE, id, ts));
        String crc = Checksum.crc32Hex(enc);
        byte[] cut = Arrays.copyOf(enc, enc.length - 4);
        Envelope e = new Envelope(id, ts, Base64.getEncoder().encodeToString(cut), crc);
        assertRejected(HttpResponseStatus.UNPROCESSABLE_ENTITY, "checksum mismatch", send(post(json(e))));
    }

    @Test
    void wrongChecksumValueFailsChecksum() throws Exception {
        Envelope e = valid();
        String wrong = e.checksum().equals("00000000") ? "00000001" : "00000000";
        Envelope bad = new Envelope(e.messageId(), e.timestamp(), e.payload(), wrong);
        assertRejected(HttpResponseStatus.UNPROCESSABLE_ENTITY, "checksum mismatch", send(post(json(bad))));
    }

    // ---------- подлинность (AES-GCM) ----------

    @Test
    void forgedMessageIdWithCorrectChecksumFailsAuthentication() throws Exception {
        // payload подписан для messageId = id, а в конверте указан id+1; CRC при этом верный
        long id = ++nextId;
        String ts = Instant.now().toString();
        byte[] enc = cipher.encrypt(TELEMETRY, Aad.build("req", DEVICE, id, ts));
        Envelope e = new Envelope(id + 1, ts, Base64.getEncoder().encodeToString(enc), Checksum.crc32Hex(enc));
        assertRejected(HttpResponseStatus.UNAUTHORIZED, "authentication failed", send(post(json(e))));
    }

    @Test
    void messageEncryptedWithAnotherKeyFailsAuthentication() throws Exception {
        AesGcmCipher other = new AesGcmCipher(Base64.getDecoder().decode(AesGcmCipher.generateKey()));
        long id = ++nextId;
        String ts = Instant.now().toString();
        byte[] enc = other.encrypt(TELEMETRY, Aad.build("req", DEVICE, id, ts));
        Envelope e = new Envelope(id, ts, Base64.getEncoder().encodeToString(enc), Checksum.crc32Hex(enc));
        assertRejected(HttpResponseStatus.UNAUTHORIZED, "authentication failed", send(post(json(e))));
    }

    @Test
    void messageForAnotherDeviceFailsAuthentication() throws Exception {
        // конверт подписан для sensor-1, но отправлен на путь другого устройства
        FullHttpRequest req = post("/devices/sensor-2/telemetry", "application/json",
                json(valid()).getBytes(StandardCharsets.UTF_8));
        assertRejected(HttpResponseStatus.UNAUTHORIZED, "authentication failed", send(req));
    }

    // ---------- содержимое после расшифровки ----------

    @Test
    void decryptedNonJsonTelemetryIsRejected() throws Exception {
        Envelope e = valid(++nextId, Instant.now().toString(), "temp=21.5".getBytes(StandardCharsets.UTF_8));
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not valid JSON", send(post(json(e))));
    }

    @Test
    void decryptedEmptyTelemetryIsRejected() throws Exception {
        Envelope e = valid(++nextId, Instant.now().toString(), new byte[0]);
        assertRejected(HttpResponseStatus.BAD_REQUEST, "not valid JSON", send(post(json(e))));
    }

    // ---------- replay ----------

    @Test
    void duplicateMessageIsRejected() throws Exception {
        String body = json(valid());

        assertNull(send(post(body)));
        FullHttpRequest first = channel.readInbound();
        assertNotNull(first);
        first.release();

        channel = newChannel();   // после отказа/первого запроса соединение новое, guard общий
        assertRejected(HttpResponseStatus.CONFLICT, "duplicate messageId", send(post(body)));
    }

    @Test
    void rejectedMessageDoesNotBurnMessageId() throws Exception {
        // битое сообщение с id не должно «занимать» этот id: повторная отправка исправного пройдёт
        long id = ++nextId;
        String ts = Instant.now().toString();
        byte[] enc = cipher.encrypt(TELEMETRY, Aad.build("req", DEVICE, id, ts));
        byte[] broken = enc.clone();
        broken[0] ^= 1;
        Envelope bad = new Envelope(id, ts, Base64.getEncoder().encodeToString(broken), Checksum.crc32Hex(enc));
        assertRejected(HttpResponseStatus.UNPROCESSABLE_ENTITY, "checksum mismatch", send(post(json(bad))));

        channel = newChannel();
        Envelope good = new Envelope(id, ts, Base64.getEncoder().encodeToString(enc), Checksum.crc32Hex(enc));
        assertNull(send(post(json(good))));
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
        passed.release();
    }

    /** Мелкий помощник: байты ByteBuf без изменения readerIndex. */
    private static final class ByteBufBytes {
        static byte[] of(ByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return bytes;
        }
    }
}