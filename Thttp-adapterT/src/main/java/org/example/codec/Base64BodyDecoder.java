package org.example.codec;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;


public class Base64BodyDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest req, List<Object> out) {
        if (!req.content().isReadable()) {
            out.add(req.retain());
            return;
        }

        byte[] decoded;
        try {
            String encoded = new String(ByteBufUtil.getBytes(req.content()), StandardCharsets.US_ASCII).trim();
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            reject(ctx);
            return;
        }

        FullHttpRequest result = req.replace(Unpooled.wrappedBuffer(decoded));
        HttpUtil.setContentLength(result, decoded.length);
        out.add(result);
    }

    private static void reject(ChannelHandlerContext ctx) {
        byte[] bytes = "body is not valid Base64".getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, Unpooled.wrappedBuffer(bytes));
        resp.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
}
