package org.example.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.example.crypto.AesGcmCipher;
import org.example.model.Envelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class EnvelopeDecoder extends MessageToMessageDecoder<FullHttpRequest> {
    public static final AttributeKey<MessageContext> CONTEXT = AttributeKey.valueOf("iot.messageContext");

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern CRC_HEX = Pattern.compile("[0-9a-fA-F]{8}");

    private final AesGcmCipher cipher;
    private final ReplayGuard replayGuard;

    public EnvelopeDecoder(AesGcmCipher cipher, ReplayGuard replayGuard) {
        this.cipher = cipher;
        this.replayGuard = replayGuard;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest req, List<Object> out) throws Exception {
        String deviceId = routeDeviceId(req);
        if (deviceId == null) {
            out.add(req.retain());
            return;
        }
        if (!DEVICE_ID.matcher(deviceId).matches()) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "invalid device id");
            return;
        }
        if (!isJson(req)) {
            reject(ctx, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
            return;
        }
        if (!req.decoderResult().isSuccess()) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "malformed HTTP request");
            return;
        }
        if (req.content().readableBytes() == 0) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "empty body");
            return;
        }

        Envelope envelope;
        try {
            envelope = MAPPER.readValue(ByteBufUtil.getBytes(req.content()), Envelope.class);
        } catch (IOException e) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "body is not a valid JSON envelope");
            return;
        }
        if (envelope == null || envelope.messageId() == null || envelope.timestamp() == null
                || envelope.payload() == null || envelope.checksum() == null) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST,
                    "messageId, timestamp, payload and checksum are required");
            return;
        }
        if (envelope.messageId() <= 0) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "messageId must be positive");
            return;
        }
        if (envelope.payload().isEmpty()) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "payload is empty");
            return;
        }
        if (!CRC_HEX.matcher(envelope.checksum()).matches()) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "checksum must be 8 hex characters (CRC-32)");
            return;
        }
        Instant sentAt;
        try {
            sentAt = Instant.parse(envelope.timestamp());
        } catch (DateTimeParseException e) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "timestamp must be ISO-8601, e.g. 2026-10-31T12:00:00Z");
            return;
        }
        if (Duration.between(sentAt, Instant.now()).abs().compareTo(MAX_CLOCK_SKEW) > 0) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "timestamp is out of the allowed window");
            return;
        }

        byte[] encrypted;
        try {
            encrypted = Base64.getDecoder().decode(envelope.payload());
        } catch (IllegalArgumentException e) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "payload is not valid Base64");
            return;
        }
        if (!Checksum.matches(encrypted, envelope.checksum())) {
            reject(ctx, HttpResponseStatus.UNPROCESSABLE_ENTITY, "checksum mismatch: message is corrupted");
            return;
        }

        byte[] plain;
        try {
            byte[] aad = Aad.build("req", deviceId, envelope.messageId(), envelope.timestamp());
            plain = cipher.decrypt(encrypted, aad);
        } catch (GeneralSecurityException e) {
            reject(ctx, HttpResponseStatus.UNAUTHORIZED, "payload authentication failed");
            return;
        }
        if (!isValidJson(plain)) {
            reject(ctx, HttpResponseStatus.BAD_REQUEST, "decrypted telemetry is not valid JSON");
            return;
        }

        if (!replayGuard.register(deviceId, envelope.messageId())) {
            reject(ctx, HttpResponseStatus.CONFLICT, "duplicate messageId");
            return;
        }
        ctx.channel().attr(CONTEXT).set(new MessageContext(deviceId, envelope.messageId()));

        FullHttpRequest result = req.replace(Unpooled.wrappedBuffer(plain));
        HttpUtil.setContentLength(result, plain.length);
        out.add(result);
    }

    private static boolean isJson(FullHttpRequest req) {
        String type = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    private static boolean isValidJson(byte[] data) {
        try {
            return data.length > 0 && MAPPER.readTree(data) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String routeDeviceId(FullHttpRequest req) {
        if (!req.method().equals(HttpMethod.POST)) {
            return null;
        }
        String[] parts = req.uri().split("/");
        if (parts.length != 4 || !parts[1].equals("devices") || !parts[3].equals("telemetry")) {
            return null;
        }
        return parts[2];
    }

    private static void reject(ChannelHandlerContext ctx, HttpResponseStatus status, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
}