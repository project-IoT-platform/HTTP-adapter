package org.example.codec;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.example.crypto.AesGcmCipher;
import org.example.model.Envelope;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
public class EnvelopeEncoder extends MessageToMessageEncoder<FullHttpResponse> {

    private final AesGcmCipher cipher;

    public EnvelopeEncoder(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, FullHttpResponse resp, List<Object> out) throws Exception {
        MessageContext message = ctx.channel().attr(EnvelopeDecoder.CONTEXT).getAndSet(null);
        if (message == null) {
            out.add(resp.retain());
            return;
        }

        String timestamp = Instant.now().toString();
        byte[] aad = Aad.build("resp", message.deviceId(), message.messageId(), timestamp);
        byte[] encrypted = cipher.encrypt(ByteBufUtil.getBytes(resp.content()), aad);

        Envelope envelope = new Envelope(
                message.messageId(), timestamp, Base64.getEncoder().encodeToString(encrypted),
                Checksum.crc32Hex(encrypted));
        byte[] json = EnvelopeDecoder.MAPPER.writeValueAsBytes(envelope);

        FullHttpResponse result = resp.replace(Unpooled.wrappedBuffer(json));
        result.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, json.length);
        out.add(result);
    }
}