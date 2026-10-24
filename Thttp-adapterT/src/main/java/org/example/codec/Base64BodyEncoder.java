package org.example.codec;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;

import java.util.Base64;
import java.util.List;
public class Base64BodyEncoder extends MessageToMessageEncoder<FullHttpResponse> {

    @Override
    protected void encode(ChannelHandlerContext ctx, FullHttpResponse resp, List<Object> out) {
        byte[] plain = ByteBufUtil.getBytes(resp.content());
        byte[] encoded = Base64.getEncoder().encode(plain);

        FullHttpResponse result = resp.replace(Unpooled.wrappedBuffer(encoded));
        HttpUtil.setContentLength(result, encoded.length);
        out.add(result);
    }
}
